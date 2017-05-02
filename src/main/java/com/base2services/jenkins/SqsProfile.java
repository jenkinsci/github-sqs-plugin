package com.base2services.jenkins;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SqsProfile to access SQS
 *
 * @author aaronwalker
 */
public class SqsProfile extends AbstractDescribableImpl<SqsProfile> implements AWSCredentials {

    public final String awsAccessKeyId;
    public final Secret awsSecretAccessKey;
    public final String sqsQueue;
    private final boolean awsUseRole;

    static final String queueUrlRegex = "^https://sqs\\.(.+?)\\.amazonaws\\.com/(.+?)/(.+)$";
    static final Pattern endpointPattern = Pattern.compile("(sqs\\..+?\\.amazonaws\\.com)");
    private final boolean urlSpecified;
    private static final Logger LOGGER = Logger.getLogger(SqsProfile.class.getName());

    private AmazonSQS client;


    @DataBoundConstructor
    public SqsProfile(String awsAccessKeyId, Secret awsSecretAccessKey, String sqsQueue, boolean awsUseRole) {
        this.awsUseRole = awsUseRole;
        if (awsUseRole) {
            this.awsAccessKeyId = "";
            this.awsSecretAccessKey = null;
        } else {
            this.awsAccessKeyId = awsAccessKeyId;
            this.awsSecretAccessKey = awsSecretAccessKey;
        }
        this.sqsQueue = sqsQueue;
        this.urlSpecified = Pattern.matches(queueUrlRegex, sqsQueue);
        this.client = null;
    }

    public String getAWSAccessKeyId() {
        return awsAccessKeyId;
    }

    public String getAWSSecretKey() {
        return awsSecretAccessKey.getPlainText();
    }

    public final boolean getAWSUseRole() {
        return this.awsUseRole;
    }

    public boolean isUseRole() {
        return awsUseRole;
    }

    public AmazonSQS getSQSClient() {
        if (client == null) {
            String endpoint = getEndpoint();

            ClientConfiguration clientConfiguration = getClientConfiguration(endpoint);

            client = new AmazonSQSClient(this, clientConfiguration);
            client.setEndpoint(endpoint);
        }
        return client;
    }

    private String getEndpoint() {
        String endpoint = "";
        if (urlSpecified) {
            Matcher endpointMatcher = endpointPattern.matcher(getSqsQueue());
            if (endpointMatcher.find()) {
                endpoint =  endpointMatcher.group(1);
            }
        }
        LOGGER.info("Resolved SQS endpoint to: " + endpoint);
        return endpoint;
    }

    private ClientConfiguration getClientConfiguration(String endpoint) {
        final ClientConfiguration config = new ClientConfiguration();

        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return config;
        }
        ProxyConfiguration proxyConfig = jenkins.proxy;
        Proxy proxy = proxyConfig == null ? Proxy.NO_PROXY : proxyConfig.createProxy(endpoint);
        if (!proxy.equals(Proxy.NO_PROXY) && proxy.address() instanceof InetSocketAddress) {
            InetSocketAddress address = (InetSocketAddress) proxy.address();
            config.setProxyHost(address.getHostName());
            config.setProxyPort(address.getPort());
            if (null != proxyConfig.getUserName()) {
                config.setProxyUsername(proxyConfig.getUserName());
                config.setProxyPassword(proxyConfig.getPassword());
            }
        }
        return config;
    }

    public String getSqsQueue() {
        return sqsQueue;
    }

    public String getQueueUrl() {
        return urlSpecified ? sqsQueue
                : createQueue(getSQSClient(), sqsQueue);
    }

    /**
     * Create a Amazon SQS queue if it does already exists
     *
     * @param sqs   Amazon SQS client
     * @param queue the name of the queue
     * @return the queue url
     */
    private String createQueue(AmazonSQS sqs, String queue) {
        for (String url : sqs.listQueues().getQueueUrls()) {
            if (url.endsWith("/" + queue)) {
                return url;
            }
        }
        //The queue wasn't found so we will create it
        return sqs.createQueue(new CreateQueueRequest(queue)).getQueueUrl();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<SqsProfile> {
        @Override
        public String getDisplayName() {
            return ""; // unused
        }

        public FormValidation doValidate(@QueryParameter String awsAccessKeyId, @QueryParameter Secret awsSecretAccessKey, @QueryParameter String sqsQueue, @QueryParameter boolean awsUseRole) throws IOException {
            boolean valid = false;
            try {

                SqsProfile profile = new SqsProfile(awsAccessKeyId,awsSecretAccessKey,sqsQueue,awsUseRole);

                String queue = profile.getQueueUrl();
                if (queue != null) {
                    return FormValidation.ok("Verified SQS Queue " + queue);
                } else {
                    return FormValidation.error("Failed to validate the account");
                }
            } catch (RuntimeException ex) {
                return FormValidation.error("Failed to validate the account");
            }
        }
    }
}
