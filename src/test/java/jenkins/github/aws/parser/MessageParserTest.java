package jenkins.github.aws.parser;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static net.sf.json.JSONObject.fromObject;

public class MessageParserTest {

    private MessageParser messageParser = new MessageParser();

    @Test
    public void extractActualGithubMessage() throws Exception {

        String snsPayload = readPayaload("sns-like-github-message.json");
        String sqsPayload = readPayaload("sqs-like-github-message.json");

        String snsMessage = messageParser.extractActualGithubMessage(snsPayload);
        String sqsMessage = messageParser.extractActualGithubMessage(sqsPayload);

        Assert.assertEquals(fromObject(snsMessage), fromObject(sqsMessage));

    }

    private String readPayaload(String resource) throws IOException {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(resource);
        return IOUtils.toString(resourceAsStream, "UTF-8");
    }

}