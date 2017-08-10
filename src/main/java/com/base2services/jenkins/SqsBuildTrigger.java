package com.base2services.jenkins;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Triggers a build when we receive a message from SQS.
 *
 * @author aaronwalker
 */
public class SqsBuildTrigger extends Trigger<Job> {

    @DataBoundConstructor
    public SqsBuildTrigger() {
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }


    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {

        private volatile List<SqsProfile> sqsProfiles = new ArrayList<SqsProfile>();

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof AbstractProject;
        }

        @Override
        public String getDisplayName() {
            return "Build when a message is published to an SQS Queue";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            JSONObject sqs = json.getJSONObject("sqsProfiles");
            sqsProfiles = req.bindJSONToList(SqsProfile.class, sqs);
            save();
            return true;
        }

        public static DescriptorImpl get() {
            return Trigger.all().get(DescriptorImpl.class);
        }

        public List<SqsProfile> getSqsProfiles() {
            return sqsProfiles;
        }

    }

}
