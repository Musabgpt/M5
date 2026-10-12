package com.musabgpt.m5;

import org.junit.Test;
import static org.junit.Assert.*;

public class PhoneProtocolTest {
    @Test public void phoneActionsAcceptStructuredCalls() throws Exception {
        AgentProtocol.Result open=AgentProtocol.parse(
            "{\"tool_calls\":[{\"name\":\"phone_open\",\"arguments\":{\"app\":\"واتساب\"}}]}");
        assertNull(open.error);
        assertEquals("phone_open",open.call.getString("name"));
        assertEquals("واتساب",open.call.getJSONObject("arguments").getString("app"));
        AgentProtocol.Result read=AgentProtocol.parse("{\"action\":\"phone_observe\"}");
        assertEquals("phone_observe",read.call.getString("name"));
        AgentProtocol.Result click=AgentProtocol.parse("{\"action\":\"phone_click\",\"id\":2}");
        assertEquals(2,click.call.getJSONObject("arguments").getInt("id"));
        AgentProtocol.Result type=AgentProtocol.parse(
            "{\"name\":\"phone_type\",\"arguments\":{\"id\":4,\"text\":\"مرحبا\"}}");
        assertEquals("مرحبا",type.call.getJSONObject("arguments").getString("text"));
        AgentProtocol.Result browser=AgentProtocol.parse(
            "{\"action\":\"phone_open_url\",\"url\":\"https://example.com\"}");
        assertEquals("phone_open_url",browser.call.getString("name"));
        AgentProtocol.Result sms=AgentProtocol.parse(
            "{\"action\":\"phone_sms_draft\",\"number\":\"07700900000\",\"text\":\"test\"}");
        assertEquals("phone_sms_draft",sms.call.getString("name"));
    }
    @Test public void modelCannotIntroduceUnregisteredPowerTools() {
        AgentProtocol.Result blocked=AgentProtocol.parse(
            "{\"action\":\"phone_install_silent\",\"package\":\"anything\"}");
        assertNull(blocked.call);
        assertNotNull(blocked.error);
    }
}
