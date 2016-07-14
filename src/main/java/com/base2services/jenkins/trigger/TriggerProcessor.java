package com.base2services.jenkins.trigger;

/**
 * Processes an payload to determine what jobs to trigger
 *
 * @author aaronwalker
 */
public interface TriggerProcessor {
    
    void trigger(String payload);
}
