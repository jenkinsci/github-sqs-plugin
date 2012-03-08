package com.base2services.jenkins;

import com.cloudbees.jenkins.GitHubPushCause;
import com.cloudbees.jenkins.GitHubRepositoryName;
import com.cloudbees.jenkins.GitHubTrigger;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.SequentialExecutionQueue;
import hudson.util.StreamTaskListener;
import net.sf.json.JSONObject;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Triggers a build when we receive a message from SQS.
 *
 * @author aaronwalker
 */
public class SqsBuildTrigger extends Trigger<AbstractProject> implements GitHubTrigger, Runnable {

    private static final Logger LOGGER = Logger.getLogger(SqsBuildTrigger.class.getName());

    @DataBoundConstructor
    public SqsBuildTrigger() {
    }

    /**
     * Called when a POST is made.
     */
    public void onPost() {
        getDescriptor().queue.execute(this);
    }

    /**
     * Returns the file that records the last/current polling activity.
     */
    public File getLogFile() {
        return new File(job.getRootDir(),"sqs-polling.log");
    }

    public void run() {
        try {
            StreamTaskListener listener = new StreamTaskListener(getLogFile());

            try {
                PrintStream logger = listener.getLogger();
                long start = System.currentTimeMillis();
                logger.println("Started on "+ DateFormat.getDateTimeInstance().format(new Date()));
                boolean result = job.poll(listener).hasChanges();
                logger.println("Done. Took "+ Util.getTimeSpanString(System.currentTimeMillis() - start));
                if(result) {
                    logger.println("Changes found");
                    job.scheduleBuild(new GitHubPushCause());
                } else {
                    logger.println("No changes");
                }
            } finally {
                listener.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"Failed to record SCM polling",e);
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Does this project read from a repository of the given user name and the
     * given repository name?
     */
    public Set<GitHubRepositoryName> getGitHubRepositories() {
        Set<GitHubRepositoryName> r = new HashSet<GitHubRepositoryName>();
        if (Hudson.getInstance().getPlugin("multiple-scms") != null
                && job.getScm() instanceof MultiSCM) {
            MultiSCM multiSCM = (MultiSCM) job.getScm();
            List<SCM> scmList = multiSCM.getConfiguredSCMs();
            for (SCM scm : scmList) {
                addRepositories(r, scm);
            }
        } else {
            addRepositories(r, job.getScm());
        }
        return r;
    }

    /**
     * @since 1.1
     */
    protected void addRepositories(Set<GitHubRepositoryName> r, SCM scm) {
        if (scm instanceof GitSCM) {
            GitSCM git = (GitSCM) scm;
            for (RemoteConfig rc : git.getRepositories()) {
                for (URIish uri : rc.getURIs()) {
                    String url = uri.toString();
                    GitHubRepositoryName repo = GitHubRepositoryName.create(url);
                    if (repo != null) {
                        r.add(repo);
                    }
                }
            }
        }
    }

    @Override
    public void start(AbstractProject project, boolean newInstance) {
        super.start(project, newInstance);
        if(newInstance && getDescriptor().isManageHook()) {
            //TODO: register sqs github web hook
        }
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {
        private transient final SequentialExecutionQueue queue = new SequentialExecutionQueue(Executors.newSingleThreadExecutor());

        private boolean manageHook = true;
        private volatile List<SqsProfile> sqsProfiles = new ArrayList<SqsProfile>();

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof AbstractProject;
        }

        @Override
        public String getDisplayName() {
            return "Build when a message is published to an SQS Queue";
        }

        public boolean isManageHook() {
            return manageHook;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            JSONObject sqs = json.getJSONObject("sqsProfiles");
            //sqsEnabled = "enabled".equals(sqs.getString("value"));
            sqsProfiles = req.bindJSONToList(SqsProfile.class,sqs);
            save();
            return true;
        }

        public static DescriptorImpl get() {
            return Trigger.all().get(DescriptorImpl.class);
        }        

        public List<SqsProfile> getSqsProfiles() {
            return sqsProfiles;
        }


    }
}
