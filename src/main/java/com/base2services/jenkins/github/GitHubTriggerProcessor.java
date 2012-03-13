package com.base2services.jenkins.github;

import com.base2services.jenkins.SqsBuildTrigger;
import com.base2services.jenkins.trigger.TriggerProcessor;
import com.cloudbees.jenkins.GitHubWebHook;

import java.util.logging.Logger;
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
        GitHubWebHook.get().processGitHubPayload(payload,SqsBuildTrigger.class);
    }
}
