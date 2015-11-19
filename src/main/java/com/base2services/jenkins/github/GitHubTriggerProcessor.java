package com.base2services.jenkins.github;

import com.base2services.jenkins.SqsBuildTrigger;
import com.base2services.jenkins.trigger.TriggerProcessor;
import com.cloudbees.jenkins.GitHubRepositoryName;
import com.cloudbees.jenkins.GitHubTrigger;
import hudson.model.AbstractProject;
import hudson.model.BooleanParameterValue;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.ACL;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
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
        if (json.has("custom_format")) {
            processCustomPayload(json, SqsBuildTrigger.class);
        }
        // The default format, e.g. payload passed directly from a GitHub webhook,
        // always includes a "repository" object.
        else if (json.has("repository")) {
            // Note that the payload will again be extracted from JSON at the start of the processGitHubPayload function.
            // Leaving it this way so as not to change the contract, to be backwards compatible with any integrations.
            processGitHubPayload(payload, SqsBuildTrigger.class);
        }
        else {
            LOGGER.warning("Unable to determine the format of the SQS message.");
        }
    }

    public void processGitHubPayload(String payload, Class<? extends Trigger> triggerClass) {
        JSONObject json = extractJsonFromPayload(payload);
        if(json == null) {
            LOGGER.warning("sqs message contains unknown payload format");
            return;
        }
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

    public void processCustomPayload(JSONObject json, Class<? extends Trigger> triggerClass) {
        // Note that custom payloads will only trigger jobs that are configured with this SQS trigger.
        // The custom payload must contain a root object named "job" of type string.
        String jobToTrigger = json.getString("job");
        if (jobToTrigger == null) {
            LOGGER.warning("Custom sqs message payload does not contain information about which job to trigger.");
            return;
        }
        // The custom payload can contain a root object named "parameters"
        // that contains a list of parameters to pass to the job when scheduled.
        // Each parameter object should contain information about its type, name, and value.
        // TODO this will only work with string or boolean parameters,
        // and you need to pass in ALL parameters for the job,
        // as the scheduled job will not fill in any defaults for you.
        List<ParameterValue> parameters = getParamsFromJson(json);

        Authentication old = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
        try {
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
                            ParametersAction pAction = new ParametersAction(parameters);
                            final QueueTaskFuture queueTaskFuture = mixin.scheduleBuild2(0, cAction, pAction);
                            if (queueTaskFuture == null) {
                                LOGGER.warning("Unable to schedule the job " + jobName);
                            }
                        }
                    } else {
                        LOGGER.warning("The job " + jobName + " is not configured as a Parameterized Job.");
                    }
                }
            }
        } finally {
            SecurityContextHolder.getContext().setAuthentication(old);
        }
    }

    private Boolean isValidParameterJson(JSONObject param) {
        if (param.has("name") && param.has("value") && param.has("type")) {
            if (param.getString("type").matches("string|boolean")) {
                return true;
            } else {
                LOGGER.warning("'string' and 'boolean' are the only supported parameter types.");
                return false;
            }
        } else {
            LOGGER.warning("Parameters must contain key/value pairs for 'name', 'value', and 'type'.");
            return false;
        }
    }

    public List<ParameterValue> getParamsFromJson(JSONObject json) {
        List<ParameterValue> params = new ArrayList<ParameterValue>();
        if (json.has("parameters")) {
            try {
                JSONArray parameters = json.getJSONArray("parameters");
                for (int i = 0; i < parameters.size(); i++) {
                    JSONObject param = (JSONObject) parameters.get(i);
                    if (isValidParameterJson(param)) {
                        String name = param.getString("name");
                        String type = param.getString("type");
                        if (type.equals("boolean")) {
                            Boolean value = param.getBoolean("value");
                            BooleanParameterValue parameterValue = new BooleanParameterValue(name, value);
                            params.add(parameterValue);
                        } else {
                            String value = param.getString("value");
                            StringParameterValue parameterValue = new StringParameterValue(name, value);
                            params.add(parameterValue);
                        }
                    }
                }
            } catch (JSONException e) {
                LOGGER.warning("Parameters must be passed as a JSONArray in the SQS message.");
            }
        }
        return params;
    }
}
