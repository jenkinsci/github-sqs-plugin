package com.base2services.jenkins;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.cloudbees.jenkins.GitHubPushTrigger;
import com.cloudbees.jenkins.GitHubWebHook;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.PeriodicWork;
import hudson.util.TimeUnit2;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Receives a message from SQS and triggers any builds
 *
 * @author aaronwalker
 */
@Extension
public class SqsQueueHandler extends PeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(SqsQueueHandler.class.getName());

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit2.SECONDS.toMillis(30);
    }

    @Override
    protected void doRun() throws Exception {
        List<SqsProfile> profiles = SqsBuildTrigger.DescriptorImpl.get().getSqsProfiles();
        for(SqsProfile profile : profiles) {
            LOGGER.fine("looking for build triggers on queue:" + profile.sqsQueue);
            AmazonSQS sqs = profile.getSQSClient();
            String queueUrl = profile.getQueueUrl();
            ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(queueUrl);
            List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
            for(Message message : messages) {
                //Process the message payload, it needs to conform to the GitHub Web-Hook JSON format
                try {
                    GitHubWebHook.get().processGitHubPayload(message.getBody(),SqsBuildTrigger.class);
                } finally {
                    //delete the message even if it failed
                    sqs.deleteMessage(new DeleteMessageRequest()
                            .withQueueUrl(queueUrl)
                            .withReceiptHandle(message.getReceiptHandle()));
                }
            }
        }
    }

    public static SqsQueueHandler get() {
        return PeriodicWork.all().get(SqsQueueHandler.class);
    }
}
