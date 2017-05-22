package jenkins.github.aws.parser;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.kohsuke.github.GHEvent;

import java.io.IOException;
import java.io.InputStream;

import static net.sf.json.JSONObject.fromObject;

public class MessageParserTest {

    private MessageParser messageParser = new MessageParser();

    @Test
    public void extractActualGithubMessage() throws Exception {

        String snsPayload = readPayload("sns-like-github-message.json");
        String sqsPayload = readPayload("sqs-like-github-message.json");

        String snsMessage = messageParser.extractActualGithubMessage(snsPayload);
        String sqsMessage = messageParser.extractActualGithubMessage(sqsPayload);

        Assert.assertEquals(fromObject(snsMessage), fromObject(sqsMessage));

    }

    @Test
    public void extractGithubPushEvent() throws Exception {
        String snsPayload = readPayload("sns-like-github-message.json");

        GHEvent eventType = messageParser.getGithubEvent(snsPayload);

        Assert.assertEquals(GHEvent.PUSH, eventType);
    }

    @Test
    public void extractGithubPullRequestEvent() throws Exception {
        String snsPayload = readPayload("sns-pull_request-github-message.json");

        GHEvent eventType = messageParser.getGithubEvent(snsPayload);

        Assert.assertEquals(GHEvent.PULL_REQUEST, eventType);

    }

    private String readPayload(String resource) throws IOException {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(resource);
        return IOUtils.toString(resourceAsStream, "UTF-8");
    }

}