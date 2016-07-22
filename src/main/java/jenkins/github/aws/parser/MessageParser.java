package jenkins.github.aws.parser;

import net.sf.json.JSONObject;

public class MessageParser {

    /**
     * The message will be received via SQS, but if Gihub was configured to submit it to SNS some extra json payload will be added.
     * This method extract the actual Github message.
     *
     * @param originalAwsMessage
     * @return the message as sent trom Github
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


}
