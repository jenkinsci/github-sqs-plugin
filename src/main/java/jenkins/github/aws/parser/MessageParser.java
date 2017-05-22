package jenkins.github.aws.parser;

import net.sf.json.JSONObject;
import org.kohsuke.github.GHEvent;

public class MessageParser {

    /**
     * The message will be received via SQS, but if Github was configured to submit it to SNS some extra json payload will be added.
     * This method extract the actual Github message.
     *
     * @param originalAwsMessage
     * @return the message as sent from Github
     */
    public String extractActualGithubMessage(String originalAwsMessage) {
        JSONObject json = JSONObject.fromObject(originalAwsMessage);
        if (json.has("Type")) {
            String msg = json.getString("Message");
            return msg;
        } else {
            return originalAwsMessage;
        }
    }


    /**
     * Parse the original sns message for the github event type.
     * @param originalAwsMessage
     * @return
     */
    public GHEvent getGithubEvent(String originalAwsMessage) {
        JSONObject json = JSONObject.fromObject(originalAwsMessage);
        String githubEvent = json.getJSONObject("MessageAttributes").getJSONObject("X-Github-Event").getString("Value");
        return GHEvent.valueOf(githubEvent.toUpperCase());
    }

}
