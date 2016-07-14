package com.base2services.jenkins;

import com.base2services.jenkins.github.SQSGitHubRepositoryName;
import com.cloudbees.jenkins.GitHubRepositoryName;
import com.cloudbees.jenkins.GitHubTrigger;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.Action;
import hudson.model.Item;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.triggers.Trigger;
import org.apache.commons.jelly.XMLOutput;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.github.GHException;
import org.kohsuke.github.GHRepository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractSqsGithubTrigger<T extends Item> extends Trigger<T> implements GitHubTrigger, Runnable {

    private static final Logger LOGGER = Logger.getLogger(AbstractSqsGithubTrigger.class.getName());

    public interface SqsTriggerDescriptor {

        boolean isManageHook();

        Executor getBuildQueue();

        List<SqsProfile> getSqsProfiles();

        List<Credential> getCredentials();
    }


    abstract SqsTriggerDescriptor getSqsTriggerDescriptor();

    /**
     * Called when a POST is made.
     */
    @Override
    public void onPost() {
        getSqsTriggerDescriptor().getBuildQueue().execute(this);
    }

    public void onPost(String username) {
        onPost();
    }

    /**
     * Returns the file that records the last/current polling activity.
     */
    public File getLogFile() {
        return new File(job.getRootDir(), "sqs-polling.log");
    }

    public final class SqsBuildTriggerPollingAction implements Action {

        public T getOwner() {
            return job;
        }

        public String getIconFileName() {
            return "clipboard.png";
        }

        public String getDisplayName() {
            return "SQS Activity Log";
        }

        public String getUrlName() {
            return "SQSActivityLog";
        }

        public String getLog() throws IOException {
            return Util.loadFile(getLogFile());
        }

        public void writeLogTo(XMLOutput out) throws IOException {
            new AnnotatedLargeText<SqsBuildTriggerPollingAction>(getLogFile(), Charset.defaultCharset(), true, this).writeHtmlTo(0, out.asWriter());
        }
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
                    GitHubRepositoryName repo = SQSGitHubRepositoryName.create(url);
                    if (repo != null) {
                        r.add(repo);
                    }
                }
            }
        }
    }

    @Override
    public void start(T project, boolean newInstance) {
        super.start(project, newInstance);
        if (newInstance && getSqsTriggerDescriptor().isManageHook()) {
            // make sure we have hooks installed. do this lazily to avoid blocking the UI thread.
            final Set<GitHubRepositoryName> names = getGitHubRepositories();

            getSqsTriggerDescriptor().getBuildQueue().execute(new Runnable() {
                public void run() {
                    OUTER:
                    for (GitHubRepositoryName name : names) {
                        for (GHRepository repo : name.resolve()) {
                            try {
                                //Currently creates the sqs hook based on the details of the
                                //first sqs profile. Need to find a clean way to map the profile
                                //to the github repo so we know how to populate the hook
                                if (createJenkinsHook(repo, getSqsTriggerDescriptor().getSqsProfiles().get(0))) {
                                    LOGGER.info("Added GitHub SQS webhook for " + name);
                                    continue OUTER;
                                }
                            } catch (Throwable e) {
                                LOGGER.log(Level.WARNING, "Failed to add GitHub webhook for " + name, e);
                            }
                        }
                    }
                }
            });
        }
    }

    @Override
    public void stop() {
        if (getSqsTriggerDescriptor().isManageHook()) {
            SqsHookCleaner cleaner = SqsHookCleaner.get();
            if (cleaner != null) {
                cleaner.onStop(this);
            }
        }
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return Collections.singleton(new SqsBuildTriggerPollingAction());
    }

    protected boolean createJenkinsHook(GHRepository repo, SqsProfile profile) {
        if (profile == null) {
            return false;
        }
        try {
            Map<String, String> config = new HashMap<String, String>();
            config.put("aws_access_key", profile.getAWSAccessKeyId());
            config.put("aws_secret_key", profile.getAWSSecretKey());
            config.put("sqs_queue_name", profile.getSqsQueue());
            repo.createHook("sqsqueue", config, null, true);
            return true;
        } catch (IOException e) {
            throw new GHException("Failed to update Github SQS hooks", e);
        }
    }

}
