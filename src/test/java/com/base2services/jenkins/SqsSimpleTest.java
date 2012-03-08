package com.base2services.jenkins;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import hudson.util.Secret;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Simple SQS Test
 *
 * @author aaronwalker
 */
@Ignore
public class SqsSimpleTest {
    
    private static final String AWS_ACCESS_KEY = "xxxx";
    private static final String AWS_SECRET_KEY = "xxxx";
    
    private static final String TEST_COMMIT_MSG = "    {\n" +
            "        \"after\":\"ea50ac0026d6d9c284e04afba1cc95d86dc3d976\",\n" +
            "        \"before\":\"501f46e557f8fc5e0fa4c88a7f4597ef597dd1bf\",\n" +
            "        \"commits\":[\n" +
            "            {\n" +
            "                \"added\":[\"b\"],\n" +
            "                \"author\":{\"email\":\"kk@kohsuke.org\",\"name\":\"Kohsuke Kawaguchi\",\"username\":\"kohsuke\"},\n" +
            "                \"id\":\"168f99f8647fdb1cb5401f3916c312c3df6affd8\",\n" +
            "                \"message\":\"another commit\",\n" +
            "                \"modified\":[],\n" +
            "                \"removed\":[],\n" +
            "                \"timestamp\":\"2010-12-08T14:31:24-08:00\",\n" +
            "                \"url\":\"https://github.com/jentrata/jentrata-msh/commit/168f99f8647fdb1cb5401f3916c312c3df6affd8\"\n" +
            "            },{\n" +
            "                \"added\":[\"d\"],\n" +
            "                \"author\":{\"email\":\"kk@kohsuke.org\",\"name\":\"Kohsuke Kawaguchi\",\"username\":\"kohsuke\"},\n" +
            "                \"id\":\"ef8a871772f3f8ffbfd475a5c5f3f822e492542b\",\n" +
            "                \"message\":\"new commit\",\n" +
            "                \"modified\":[],\n" +
            "                \"removed\":[],\n" +
            "                \"timestamp\":\"2010-12-08T14:32:11-08:00\",\n" +
            "                \"url\":\"https://github.com/jentrata/jentrata-msh/commit/ef8a871772f3f8ffbfd475a5c5f3f822e492542b\"\n" +
            "            }\n" +
            "        ],\n" +
            "        \"compare\":\"https://github.com/jentrata/jentrata-msh/compare/501f46e...ea50ac0\",\n" +
            "        \"forced\":false,\n" +
            "        \"pusher\":{\"email\":\"kk@kohsuke.org\",\"name\":\"kohsuke\"},\n" +
            "        \"ref\":\"refs/heads/master\",\n" +
            "        \"repository\":{\n" +
            "            \"created_at\":\"2010/12/08 12:44:13 -0800\",\n" +
            "            \"description\":\"testing\",\n" +
            "            \"fork\":false,\n" +
            "            \"forks\":1,\n" +
            "            \"has_downloads\":true,\n" +
            "            \"has_issues\":true,\n" +
            "            \"has_wiki\":true,\n" +
            "            \"homepage\":\"testing\",\n" +
            "            \"name\":\"jentrata-msh\",\n" +
            "            \"open_issues\":0,\n" +
            "            \"owner\":{\"email\":\"aaron@jentrata.org\",\"name\":\"jentrata\"},\n" +
            "            \"private\":false,\n" +
            "            \"pushed_at\":\"2010/12/08 14:32:23 -0800\",\n" +
            "            \"url\":\"https://github.com/jentrata/jentrata-msh\",\"watchers\":1\n" +
            "        }\n" +
            "    }\n" +
            "";

    private AmazonSQS sqs;

    @Before
    public void setup() {
        sqs = new AmazonSQSClient(new AWSCredentials() {
            public String getAWSAccessKeyId() {
                return AWS_ACCESS_KEY;
            }

            public String getAWSSecretKey() {
                return AWS_SECRET_KEY;
            }
        });
    }
    
    @Test
    public void testListQueues() {
        for(String url : sqs.listQueues().getQueueUrls()) {
            System.out.println("Queue url:" + url);
        }
    }

    @Test
    public void testCreateQueue() {
        CreateQueueResult result = sqs.createQueue(new CreateQueueRequest("testQueue"));
        assertNotNull(result);
        System.out.println("Queue url:" + result.getQueueUrl());
        assertTrue(result.getQueueUrl().contains("testQueue"));
    }

    @Test
    public void testCommitMessage() {
        String queueUrl = getQueueUrl("testQueue");
        sqs.sendMessage(new SendMessageRequest(queueUrl, TEST_COMMIT_MSG));
    }

    @Test
    public void testSendAndReceiveMessage() {
        
        String queueUrl = getQueueUrl("testQueue");
        //Send Message
        sqs.sendMessage(new SendMessageRequest(queueUrl, "This is my message text."));
       ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(queueUrl);
        List<Message> messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
        for (Message message : messages) {
            System.out.println("  Message");
            System.out.println("    MessageId:     " + message.getMessageId());
            System.out.println("    ReceiptHandle: " + message.getReceiptHandle());
            System.out.println("    MD5OfBody:     " + message.getMD5OfBody());
            System.out.println("    Body:          " + message.getBody());
            for (Map.Entry<String, String> entry : message.getAttributes().entrySet()) {
                System.out.println("  Attribute");
                System.out.println("    Name:  " + entry.getKey());
                System.out.println("    Value: " + entry.getValue());
            }
        }
        System.out.println();        
    }

    private String getQueueUrl(String queueName) {
        ListQueuesResult queues = sqs.listQueues();
        for(String url : sqs.listQueues().getQueueUrls()) {
            if(url.endsWith(queueName)) {
                return url;
            }
        }
        //Didn't find the queue so create it
        return sqs.createQueue(new CreateQueueRequest(queueName)).getQueueUrl();
    }
}
