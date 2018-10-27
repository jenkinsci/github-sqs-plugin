package jenkins.github.aws.parser;

import net.sf.json.JSONObject;
import org.kohsuke.github.GHEvent;

public class MessageParser {

    /**
     * The message will be received via SQS, but if Github was configured to submit it to SNS some extra json payload will be added.
     * This method extracts the actual Github message.
     *
     * @param originalAwsMessage the original AWS message to parse
     * @return the message as sent from Github
     */
    public String extractActualGithubMessage(String originalAwsMessage) {
        JSONObject json = JSONObject.fromObject(originalAwsMessage);
        if (json.has("Type")) {
            return json.getString("Message");
        } else {
            return originalAwsMessage;
        }
    }


    /**
     * Parses the original SNS message for the Github event type.
     *
     * @param originalAwsMessage the original AWS message to parse
     * @return the Github event type
     */
    public GHEvent getGithubEvent(String originalAwsMessage) {
        JSONObject json = JSONObject.fromObject(originalAwsMessage);
        String githubEvent = json.getJSONObject("MessageAttributes").getJSONObject("X-Github-Event").getString("Value");
        return GHEvent.valueOf(githubEvent.toUpperCase());
    }

}
