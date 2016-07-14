package com.base2services.jenkins;

import com.cloudbees.jenkins.GitHubRepositoryName;
import hudson.Extension;
import hudson.Util;
import hudson.model.Cause;
import hudson.model.Item;
import hudson.scm.SCM;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.SequentialExecutionQueue;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Triggers a build when we receive a message from SQS.
 *
 * @author aaronwalker
 */
public class SqsWorkflowJobBuildTrigger extends AbstractSqsGithubTrigger<WorkflowJob> {

    private static final Logger LOGGER = Logger.getLogger(SqsWorkflowJobBuildTrigger.class.getName());

    @DataBoundConstructor
    public SqsWorkflowJobBuildTrigger() {
    }

    @Override
    public SqsTriggerDescriptor getSqsTriggerDescriptor() {
        return DescriptorImpl.get();
    }

    public void run() {
        try {
            StreamTaskListener listener = new StreamTaskListener(getLogFile());

            try {
                PrintStream logger = listener.getLogger();
                long start = System.currentTimeMillis();
                logger.println("Started on " + DateFormat.getDateTimeInstance().format(new Date()));
                boolean result = job.poll(listener).hasChanges();
                logger.println("Done. Took " + Util.getTimeSpanString(System.currentTimeMillis() - start));
                if (result) {
                    logger.println("Changes found");
                    // Fix for JENKINS-16617, JENKINS-16669
                    // The Cause instance needs to have a unique identity (when equals() is called), otherwise
                    // scheduleBuild() returns false - indicating that this job is already in the queue or
                    // has already been processed.
                    if (job.scheduleBuild(new Cause.RemoteCause("GitHub via SQS", "SQS poll initiated on " +
                            DateFormat.getDateTimeInstance().format(new Date(start))))) {
                        logger.println("Job queued");
                    } else {
                        logger.println("Job NOT queued - it was determined that this job has been queued already.");
                    }
                } else {
                    logger.println("No changes");
                }
            } finally {
                listener.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to record SCM polling", e);
        }
    }


    /**
     * Does this project read from a repository of the given user name and the
     * given repository name?
     */
    @Override
    public Set<GitHubRepositoryName> getGitHubRepositories() {
        Set<GitHubRepositoryName> gitHubRepositoryNames = new HashSet<GitHubRepositoryName>();
        for (SCM scm : job.getSCMs()) {
            if (Jenkins.getInstance().getPlugin("multiple-scms") != null && scm instanceof MultiSCM) {
                MultiSCM multiSCM = (MultiSCM) scm;
                for (SCM _scm : multiSCM.getConfiguredSCMs()) {
                    addRepositories(gitHubRepositoryNames, _scm);
                }
                break;
            } else {
                addRepositories(gitHubRepositoryNames, scm);
            }
        }

        // Checking the WorkflowJobRepo
        FlowDefinition definition = job.getDefinition();
        if (definition instanceof CpsScmFlowDefinition) {
            CpsScmFlowDefinition cpsScmFlowDefinition = (CpsScmFlowDefinition) definition;
            addRepositories(gitHubRepositoryNames, cpsScmFlowDefinition.getScm());
        }

        return gitHubRepositoryNames;
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor implements SqsTriggerDescriptor {

        private transient final SequentialExecutionQueue queue = new SequentialExecutionQueue(Executors.newSingleThreadExecutor());

        private boolean manageHook = true;

        private volatile List<SqsProfile> sqsProfiles = new ArrayList<SqsProfile>();

        private volatile List<Credential> credentials = new ArrayList<Credential>();

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Item item) {

            boolean isWorkflowJob = false;

            /**
             * Checking is workflow-aggregator plugin is installed (optional dependency)
             * https://wiki.jenkins-ci.org/display/JENKINS/Dependencies+among+plugins
             */
            if (Jenkins.getInstance().getPlugin("workflow-aggregator") != null) {
                Class<?> foo;
                try {
                    foo = Class.forName("org.jenkinsci.plugins.workflow.job.WorkflowJob");
                } catch (ClassNotFoundException e) {
                    LOGGER.info("Could not find class `org.jenkinsci.plugins.workflow.job.WorkflowJob` this should not have happened as the plugin is available");
                    foo = null;
                }
                isWorkflowJob = (foo != null && foo.isAssignableFrom(item.getClass()));
            }

            return isWorkflowJob;
        }

        @Override
        public String getDisplayName() {
            return "Build when a message is published to an SQS Queue";
        }

        public boolean isManageHook() {
            return manageHook;
        }

        public Executor getBuildQueue() {
            return queue;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            JSONObject sqs = json.getJSONObject("sqsProfiles");
            JSONObject hookMode = json.getJSONObject("sqsHookMode");
            manageHook = "auto".equals(hookMode.getString("value"));
            sqsProfiles = req.bindJSONToList(SqsProfile.class, sqs);
            credentials = req.bindJSONToList(Credential.class, hookMode.get("credentials"));
            save();
            return true;
        }

        public static DescriptorImpl get() {
            return Trigger.all().get(DescriptorImpl.class);
        }

        public List<SqsProfile> getSqsProfiles() {
            return sqsProfiles;
        }

        public List<Credential> getCredentials() {
            return credentials;
        }

    }

}
