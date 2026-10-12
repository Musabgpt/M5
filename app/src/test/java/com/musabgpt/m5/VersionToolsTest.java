package com.musabgpt.m5;

import org.junit.Test;
import static org.junit.Assert.*;

/** Regression coverage: AI tool parser must recognize genuine local history/Git tools. */
public class VersionToolsTest {
    @Test public void parsesHistoryOperationsAndCurrentHash() throws Exception {
        AgentProtocol.Result listing=AgentProtocol.parse(
            "{\"action\":\"history_list\",\"path\":\"Demo/main.js\"}");
        assertNull(listing.error);
        assertEquals("history_list",listing.call.getString("name"));
        assertEquals("Demo/main.js",listing.call.getJSONObject("arguments").getString("path"));

        AgentProtocol.Result restore=AgentProtocol.parse(
            "{\"tool_calls\":[{\"name\":\"history_restore\",\"arguments\":{\"path\":\"Demo/main.js\",\"id\":\"1234567890000-0123456789\",\"expectedHash\":\"abc\"}}]}");
        assertNull(restore.error);
        assertEquals("history_restore",restore.call.getString("name"));
        assertEquals("1234567890000-0123456789",restore.call.getJSONObject("arguments").getString("id"));
    }

    @Test public void parsesOnlyProjectScopedGitActions() throws Exception {
        AgentProtocol.Result status=AgentProtocol.parse(
            "{\"name\":\"git_project_status\",\"arguments\":{\"path\":\"Calculator\"}}");
        assertNull(status.error);
        assertEquals("Calculator",status.call.getJSONObject("arguments").getString("path"));

        AgentProtocol.Result init=AgentProtocol.parse(
            "{\"action\":\"git_project_init\",\"path\":\"Calculator\"}");
        assertEquals("git_project_init",init.call.getString("name"));

        AgentProtocol.Result dangerous=AgentProtocol.parse(
            "{\"action\":\"git_push_force\",\"command\":\"git push --force\"}");
        assertNull(dangerous.call);
        assertNotNull(dangerous.error);
    }
}
