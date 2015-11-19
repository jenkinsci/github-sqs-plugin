package com.base2services.jenkins;

import com.base2services.jenkins.github.GitHubTriggerProcessor;
import hudson.model.BooleanParameterValue;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class GetParamsFromJsonTest {

    private String payload;
    private GitHubTriggerProcessor gtp;

    @Before
    public void setUp() throws Exception {
        gtp = new GitHubTriggerProcessor();
    }

    @Test
    public void testEmptyParams() throws Exception {
        payload = "{'job': 'testProject'}".replace("'", "\"");
        JSONObject json = gtp.extractJsonFromPayload(payload);
        List<ParameterValue> parameterValues = gtp.getParamsFromJson(json);
        assert parameterValues.size() == 0;
    }

    @Test
    public void testNonArrayParams() throws Exception {
        payload = "{'parameters': 'foo'}".replace("'", "\"");
        JSONObject json = gtp.extractJsonFromPayload(payload);
        List<ParameterValue> parameterValues = gtp.getParamsFromJson(json);
        assert parameterValues.size() == 0;
    }

    @Test
    public void testEmptyParamsArray() throws Exception {
        payload = "{'parameters': []}".replace("'", "\"");
        JSONObject json = gtp.extractJsonFromPayload(payload);
        List<ParameterValue> parameterValues = gtp.getParamsFromJson(json);
        assert parameterValues.size() == 0;
    }

    @Test
    public void testInvalidParameterType() throws Exception {
        payload = "{'parameters': [{'name': 'foo', 'value': 'bar', 'type': 'blah'}]}".replace("'", "\"");
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();
        JSONObject json = gtp.extractJsonFromPayload(payload);
        List<ParameterValue> parameterValues = gtp.getParamsFromJson(json);
        assert parameterValues.size() == 0;
    }

    @Test
    public void testStringParameter() throws Exception {
        payload = "{'parameters': [{'name': 'foo', 'value': 'bar', 'type': 'string'}]}".replace("'", "\"");
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();
        JSONObject json = gtp.extractJsonFromPayload(payload);
        List<ParameterValue> parameterValues = gtp.getParamsFromJson(json);
        assert parameterValues.size() == 1;
        assert parameterValues.get(0).getClass().equals(StringParameterValue.class);
        assert parameterValues.get(0).getName().equals("foo");
        assert parameterValues.get(0).getValue().equals("bar");
    }

    @Test
    public void testBooleanParameter() throws Exception {
        payload = "{'parameters': [{'name': 'foo', 'value': true, 'type': 'boolean'}]}".replace("'", "\"");
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();
        JSONObject json = gtp.extractJsonFromPayload(payload);
        List<ParameterValue> parameterValues = gtp.getParamsFromJson(json);
        assert parameterValues.size() == 1;
        assert parameterValues.get(0).getClass().equals(BooleanParameterValue.class);
        assert parameterValues.get(0).getName().equals("foo");
        assert parameterValues.get(0).getValue().equals(true);
    }

    @Test
    public void testMultipleParameters() throws Exception {
        String payload_string = String.format("{'parameters': [%s]}",
            "{'name': 'foo', 'value': 'bar', 'type': 'string'}, {'name': 'hello', 'value': 'world', 'type': 'string'}");
        payload = payload_string.replace("'", "\"");
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();
        JSONObject json = gtp.extractJsonFromPayload(payload);
        List<ParameterValue> parameterValues = gtp.getParamsFromJson(json);
        assert parameterValues.size() == 2;
        assert parameterValues.get(0).getName().equals("foo");
        assert parameterValues.get(0).getValue().equals("bar");
        assert parameterValues.get(1).getName().equals("hello");
        assert parameterValues.get(1).getValue().equals("world");
    }

    @Test
    public void testInvalidParametersAreIgnored() throws Exception {
        String payload_string = String.format("{'parameters': [%s]}",
            "{'ihavenoname': 'blah'},{'name': 'foo', 'value': 'bar', 'type': 'string'}");
        payload = payload_string.replace("'", "\"");
        GitHubTriggerProcessor gtp = new GitHubTriggerProcessor();
        JSONObject json = gtp.extractJsonFromPayload(payload);
        List<ParameterValue> parameterValues = gtp.getParamsFromJson(json);
        assert parameterValues.size() == 1;
        assert parameterValues.get(0).getName().equals("foo");
        assert parameterValues.get(0).getValue().equals("bar");
    }
}
