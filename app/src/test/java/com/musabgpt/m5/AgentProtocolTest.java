package com.musabgpt.m5;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

/** Regression: different free models format tool requests differently. */
public class AgentProtocolTest {
    @Test public void canonicalToolCalls() throws Exception {
        AgentProtocol.Result result=AgentProtocol.parse(
            "{\"tool_calls\":[{\"name\":\"list_files\",\"arguments\":{}}]}");
        assertNull(result.error);
        assertEquals("list_files",result.call.getString("name"));
    }

    @Test public void fencedLegacyActionCommand() throws Exception {
        String answer=""+(char)96+(char)96+(char)96+"json\n"
             + "{\"action\":\"run_command\",\"command\":\"pwd\"}\n"
             + (char)96+(char)96+(char)96;
        AgentProtocol.Result result=AgentProtocol.parse(answer);
        assertNull(result.error);
        assertEquals("run_command",result.call.getString("name"));
        assertEquals("pwd",result.call.getJSONObject("arguments").getString("command"));
    }

    @Test public void acceptsFunctionToolCall() throws Exception {
        AgentProtocol.Result result=AgentProtocol.parse(
            "{\"tool_calls\":[{\"function\":{\"name\":\"read_file\","
          + "\"arguments\":\"{\\\"path\\\":\\\"index.py\\\"}\"}}]}");
        assertNull(result.error);
        assertEquals("read_file",result.call.getString("name"));
        assertEquals("index.py",result.call.getJSONObject("arguments").getString("path"));
    }

    @Test public void unsupportedToolIsNotMistakenForChatOrExecuted() {
        AgentProtocol.Result result=AgentProtocol.parse(
            "{\"action\":\"delete_files\",\"command\":\"rm -rf .\"}");
        assertNull(result.call);
        assertTrue(result.error.contains("Unsupported"));
    }

    @Test public void plainConversationDoesNotCallTools() {
        AgentProtocol.Result result=AgentProtocol.parse("مرحبا! كيف أقدر أساعدك؟");
        assertNull(result.call);
        assertNull(result.error);
    }

    @Test public void multiCallIsRejectedToEnforcePerActionConsent() {
        AgentProtocol.Result result=AgentProtocol.parse(
            "{\"tool_calls\":[{\"name\":\"list_files\",\"arguments\":{}},"
           + "{\"name\":\"run_command\",\"arguments\":{\"command\":\"pwd\"}}]}");
        assertNull(result.call);
        assertTrue(result.error.contains("one"));
    }

    @Test public void invalidToolArgumentsFailClosed() {
        AgentProtocol.Result result=AgentProtocol.parse(
            "{\"action\":\"run_command\",\"arguments\":\"{not json\"}");
        assertNull(result.call);
        assertNotNull(result.error);
    }
}
