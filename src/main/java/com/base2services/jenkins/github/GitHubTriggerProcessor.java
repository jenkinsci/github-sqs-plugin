package com.base2services.jenkins.github;

import com.base2services.jenkins.SqsBuildTrigger;
import com.base2services.jenkins.trigger.TriggerProcessor;
import com.cloudbees.jenkins.GitHubRepositoryName;
import com.cloudbees.jenkins.GitHubTrigger;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.security.ACL;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes a github commit hook payload
 *
 * @author aaronwalker
 */
public class GitHubTriggerProcessor implements TriggerProcessor {

    private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+)");
    private static final Logger LOGGER = Logger.getLogger(GitHubTriggerProcessor.class.getName());

    public void trigger(String payload) {
        JSONObject json = extractJsonFromPayload(payload);
        // You can signal through the payload that you will be using a custom format
        // by including a root object named "custom_format" of any type and value.
        if(json.has("custom_format")) {
            processCustomPayload(json, SqsBuildTrigger.class);
        }
        // The default format, e.g. payload passed directly from a GitHub webhook,
        // always includes a "repository" object.
        else if(json.has("repository")) {
            processGitHubPayload(json, SqsBuildTrigger.class);
        }
        else {
            LOGGER.warning("sqs message contains unknown payload format");
        }
    }

    public void processGitHubPayload(JSONObject json, Class<? extends Trigger> triggerClass) {
        JSONObject repository = json.getJSONObject("repository");
        String repoUrl = repository.getString("url"); // something like 'https://github.com/kohsuke/foo'
        String repoName = repository.getString("name"); // 'foo' portion of the above URL
        String ownerName = repository.getJSONObject("owner").getString("name"); // 'kohsuke' portion of the above URL

        LOGGER.info("Received Message for " + repoUrl);
        LOGGER.fine("Full details of the POST was " + json.toString());
        Matcher matcher = REPOSITORY_NAME_PATTERN.matcher(repoUrl);
        if (matcher.matches()) {
            // run in high privilege to see all the projects anonymous users don't see.
            // this is safe because when we actually schedule a build, it's a build that can
            // happen at some random time anyway.
            Authentication old = SecurityContextHolder.getContext().getAuthentication();
            SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
            try {
                GitHubRepositoryName changedRepository = new SQSGitHubRepositoryName(matcher.group(1), ownerName, repoName);
                for (AbstractProject<?,?> job : Hudson.getInstance().getAllItems(AbstractProject.class)) {
                    GitHubTrigger trigger = (GitHubTrigger) job.getTrigger(triggerClass);
                    if (trigger!=null) {
                        LOGGER.fine("Considering to poke "+job.getFullDisplayName());
                        if (trigger.getGitHubRepositories().contains(changedRepository)) {
                            LOGGER.info("Poked "+job.getFullDisplayName());
                            trigger.onPost();
                        } else
                            LOGGER.fine("Skipped "+job.getFullDisplayName()+" because it doesn't have a matching repository.");
                    }
                }
            } finally {
                SecurityContextHolder.getContext().setAuthentication(old);
            }
        } else {
            LOGGER.warning("Malformed repo url "+repoUrl);
        }
    }

    public void processCustomPayload(JSONObject json, Class<? extends Trigger> triggerClass) {
        // Note that custom payloads will only trigger parameterized jobs that are configured with this SQS trigger.
        // The custom payload must contain a root object named "job" of type string.
        String jobToTrigger = json.getString("job");
        if (jobToTrigger == null) {
            LOGGER.warning("Custom sqs message payload does not contain data about the job to trigger.");
            return;
        }
        // The custom payload must contain a root object named "parameters"
        // that contains a list of parameters to pass to the job when scheduled.
        // Each parameter object has information about the its type, name, and value.
        Authentication old = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
        try {
            JSONArray parameters = json.getJSONArray("parameters");
            Jenkins jenkins = Jenkins.getInstance();
            for (Job job: jenkins.getAllItems(Job.class)) {
                String jobName = job.getDisplayName();
                if (jobName.equals(jobToTrigger)) {
                    // Custom triggers operate on Parameterized jobs only
                    if (job instanceof ParameterizedJobMixIn.ParameterizedJob) {
                        // Make sure the job is configured to use the SQS trigger
                        ParameterizedJobMixIn.ParameterizedJob pJob = (ParameterizedJobMixIn.ParameterizedJob) job;
                        final Map<TriggerDescriptor, Trigger<?>> pJobTriggers = pJob.getTriggers();
                        SqsBuildTrigger.DescriptorImpl descriptor = jenkins.getDescriptorByType(SqsBuildTrigger.DescriptorImpl.class);
                        if (!pJobTriggers.containsKey(descriptor)) {
                            LOGGER.warning("The job " + jobName + " is not configured to use the SQS trigger.");
                        } else {
                            final Job theJob = job;
                            ParameterizedJobMixIn mixin = new ParameterizedJobMixIn() {
                                @Override
                                protected Job asJob() {
                                    return theJob;
                                }
                            };
                            Cause cause = new Cause.RemoteCause("SQS", "Triggered by SQS.");
                            CauseAction cAction = new CauseAction(cause);
                            // TODO this will only work with string parameters, and you will
                            // need to pass in ALL parameters, as it will not fill in any defaults for you.
                            List<ParameterValue> params = new ArrayList<ParameterValue>();
                            for (int i = 0; i < parameters.size(); i++) {
                                JSONObject param = (JSONObject)parameters.get(i);
                                String name = param.optString("name", "xyz");
                                String value = param.optString("value", "abc");
                                StringParameterValue parameterValue = new StringParameterValue(name, value);
                                params.add(parameterValue);
                            }
                            ParametersAction pAction = new ParametersAction(params);
                            mixin.scheduleBuild2(0, cAction, pAction); // TODO check the return value for null / failure
                        }
                    }
                }
            }
        } finally {
            SecurityContextHolder.getContext().setAuthentication(old);
        }
    }

    public JSONObject extractJsonFromPayload(String payload) {
        JSONObject json = JSONObject.fromObject(payload);
        if(json.has("Type")) {
            String msg = json.getString("Message");
            if(msg != null) {
                char ch[] = msg.toCharArray();
                if((ch[0] == '"') && (ch[msg.length()-1]) == '"') {
                   msg = msg.substring(1,msg.length()-1); //remove the leading and trailing double quotes
                }
                return JSONObject.fromObject(msg);
            }
        }
        return json;
    }
}
