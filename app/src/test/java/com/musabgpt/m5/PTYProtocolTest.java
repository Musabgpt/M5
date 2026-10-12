package com.musabgpt.m5;
import org.junit.Test;
import static org.junit.Assert.*;
public class PTYProtocolTest {
    @Test public void acceptsSessionOperationsAsStructuredActions()throws Exception{
        AgentProtocol.Result exec=AgentProtocol.parse(
          "{\"action\":\"exec_command\",\"command\":\"python -m pytest -q\",\"workdir\":\"Demo\",\"yield_time_ms\":10000}");
        assertNull(exec.error);
        assertEquals("exec_command",exec.call.getString("name"));
        assertEquals("Demo",exec.call.getJSONObject("arguments").getString("workdir"));
        AgentProtocol.Result stdin=AgentProtocol.parse(
          "{\"action\":\"write_stdin\",\"session_id\":\"aabb\",\"text\":\"y\\n\"}");
        assertEquals("write_stdin",stdin.call.getString("name"));
        AgentProtocol.Result read=AgentProtocol.parse(
          "{\"action\":\"read_output\",\"session_id\":\"aabb\",\"cursor\":3,\"yield_time_ms\":2500}");
        assertEquals(3,read.call.getJSONObject("arguments").getInt("cursor"));
        AgentProtocol.Result stop=AgentProtocol.parse(
          "{\"action\":\"terminate_process\",\"session_id\":\"aabb\"}");
        assertEquals("terminate_process",stop.call.getString("name"));
    }
    @Test public void unsupportedTermuxPrivilegesRemainRejected(){
        AgentProtocol.Result blocked=AgentProtocol.parse("{\"action\":\"root_shell\",\"command\":\"id\"}");
        assertNull(blocked.call);
        assertNotNull(blocked.error);
    }
}
