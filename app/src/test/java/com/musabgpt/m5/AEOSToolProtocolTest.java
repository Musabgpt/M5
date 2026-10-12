package com.musabgpt.m5;
import org.junit.Test;
import static org.junit.Assert.*;
public class AEOSToolProtocolTest {
    @Test public void parsedDiffHasStructuredShaAndExplicitPath()throws Exception{
        AgentProtocol.Result result=AgentProtocol.parse(
            "{\"action\":\"apply_patch\",\"path\":\"src/main.js\",\"diff\":\"--- a/src/main.js\","
          + "\"expectedHash\":\"abc\"}");
        assertNull(result.error);
        assertEquals("apply_patch",result.call.getString("name"));
        assertEquals("src/main.js",result.call.getJSONObject("arguments").getString("path"));
        assertEquals("abc",result.call.getJSONObject("arguments").getString("expectedHash"));
    }
    @Test public void CDPRequiresExplicitToolRatherThanExecutingModelText()throws Exception{
        AgentProtocol.Result result=AgentProtocol.parse(
            "{\"action\":\"browser_observe\",\"port\":\"9222\"}");
        assertNull(result.error);
        assertEquals("9222",result.call.getJSONObject("arguments").getString("port"));
        AgentProtocol.Result forbidden=AgentProtocol.parse(
            "{\"action\":\"browser_execute_arbitrary_script\",\"script\":\"alert(1)\"}");
        assertNull(forbidden.call);
        assertNotNull(forbidden.error);
    }
}
