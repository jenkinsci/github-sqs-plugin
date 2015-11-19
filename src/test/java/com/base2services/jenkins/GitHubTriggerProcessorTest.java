package com.base2services.jenkins;

import java.util.HashSet;
import java.util.Set;

import com.base2services.jenkins.github.GitHubTriggerProcessor;
import com.cloudbees.jenkins.GitHubRepositoryName;
import hudson.model.FreeStyleProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Created by benpatterson on 11/16/15.
 */
@RunWith(MockitoJUnitRunner.class)
public class GitHubTriggerProcessorTest {

    private FreeStyleProject project;
    private String payload;

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Mock
    private SqsBuildTrigger sbt;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        project = jenkinsRule.createFreeStyleProject("testProject");
        project.addTrigger(sbt);
        GitHubRepositoryName repoName = new GitHubRepositoryName("github.com", "foo", "bar");
        Set<GitHubRepositoryName> repoSet = new HashSet<GitHubRepositoryName>();
        repoSet.add(repoName);
        doReturn(repoSet).when(sbt).getGitHubRepositories();
    }

    @Test
    public void shouldTriggerWhenRepoInPayload() throws Exception {
        payload = "{'repository': {'url': 'https://github.com/foo/bar', 'owner': {'name': 'foo'}, 'name': 'bar'}}".replace("'", "\"");
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();
        gtp.processGitHubPayload(payload, sbt.getClass());
        verify(sbt).onPost();
    }

    @Test
    public void shouldNotTriggerWithWrongRepo() throws Exception {
        payload = "{'repository': {'url': 'https://github.com/foo/baz', 'owner': {'name': 'foo'}, 'name': 'baz'}}".replace("'", "\"");
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();
        gtp.processGitHubPayload(payload, sbt.getClass());
        verify(sbt, never()).onPost();
    }

    @Test
    public void shouldNotTriggerWithAFork() throws Exception {
        payload = "{'repository': {'url': 'https://github.com/fob/bar', 'owner': {'name': 'fob'}, 'name': 'bar'}}".replace("'", "\"");
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();
        gtp.processGitHubPayload(payload, sbt.getClass());
        verify(sbt, never()).onPost();
    }

    @Test
    public void shouldNotTriggerWithBadPayload() throws Exception {
        payload = "{'noRepoInfo': {'empty': 'https://github.com/foo/bar'}}".replace("'", "\"");
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();
        gtp.trigger(payload);
        verify(sbt, never()).onPost();
    }

    @Test
    public void shouldTriggerUnquotedSnsMsg() throws Exception {
        payload = "{\"Type\" : \"Notification\", \"Message\" : \"{'repository': {'owner': {'name': 'foo'}, 'url': 'https://github.com/foo/bar', 'name': 'bar'}}\", }";
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();
        gtp.processGitHubPayload(payload, sbt.getClass());
        verify(sbt).onPost();
    }
}
