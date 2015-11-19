package com.base2services.jenkins;

import com.base2services.jenkins.github.GitHubTriggerProcessor;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;


@RunWith(MockitoJUnitRunner.class)
public class ProcessCustomPayloadTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    private FreeStyleProject project;
    private String payload;
    private SqsBuildTrigger sbt;

    @Before
    public void setUp() throws Exception {
        sbt = new SqsBuildTrigger();
        project = jenkinsRule.createFreeStyleProject("testProject");
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                return false;
            }
        });
        project.addTrigger(sbt);
        jenkinsRule.jenkins.doQuietDown(); // Put jenkins in quiet down so that tasks will stay in the queue.
    }

    @Test
    public void customMsgModeShouldPerformBuild() throws Exception {
        payload = "{'job': 'testProject'}".replace("'", "\"");
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();

        gtp.processCustomPayload(gtp.extractJsonFromPayload(payload), sbt.getClass());
        assert (jenkinsRule.jenkins.getQueue().getItems(project).size() == 1);
    }

    @Test
    public void shouldBuildWithBooleanParameter() throws Exception {
        String payload_string = String.format("{'job': 'testProject', 'parameters': [%s]}",
                "{'name': 'foo', 'value': true, 'type': 'boolean'}");
        payload = payload_string.replace("'", "\"");
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();

        gtp.processCustomPayload(gtp.extractJsonFromPayload(payload), sbt.getClass());
        final Queue.Item queueItem = jenkinsRule.jenkins.getQueue().getItem(project);
        final String params = queueItem.getParams();
        assert (params.equals("\n" + "foo=true"));
    }

    @Test
    public void shouldBuildWithStringParameter() throws Exception {
        String payload_string = String.format("{'job': 'testProject', 'parameters': [%s]}",
                "{'name': 'bar', 'value': 'baz', 'type': 'string'}");
        payload = payload_string.replace("'", "\"");
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();

        gtp.processCustomPayload(gtp.extractJsonFromPayload(payload), sbt.getClass());
        final Queue.Item queueItem = jenkinsRule.jenkins.getQueue().getItem(project);
        final String params = queueItem.getParams();
        assert (params.equals("\n" + "bar=baz"));
    }

    @Test
    public void shouldBuildOnlySpecifiedJob() throws Exception {
        FreeStyleProject project2 = jenkinsRule.createFreeStyleProject("secondProject");
        project2.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                return false;
            }
        });
        project2.addTrigger(sbt);

        payload = "{'job': 'secondProject'}]}".replace("'", "\"");
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();

        gtp.processCustomPayload(gtp.extractJsonFromPayload(payload), sbt.getClass());
        assert (jenkinsRule.jenkins.getQueue().getItems(project).size() == 0);
        assert (jenkinsRule.jenkins.getQueue().getItems(project2).size() == 1);
    }

    @Test
    public void shouldNotBuildIfJobDoesNotHaveTrigger() throws Exception {
        FreeStyleProject project2 = jenkinsRule.createFreeStyleProject("secondProject");
        project2.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                return false;
            }
        });

        payload = "{'job': 'secondProject'}]}".replace("'", "\"");
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();

        gtp.processCustomPayload(gtp.extractJsonFromPayload(payload), sbt.getClass());
        assert (jenkinsRule.jenkins.getQueue().getItems(project).size() == 0);
        assert (jenkinsRule.jenkins.getQueue().getItems(project2).size() == 0);
    }
}
