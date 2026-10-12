package com.musabgpt.m5;

import org.junit.Test;
import static org.junit.Assert.*;

public class OfflineFallbackTest {
    @Test public void recognizesOnlySafeConcreteTemplates(){
        assertEquals("web-calculator",OfflineFallback.match("أنشئ آلة حاسبة بلغة HTML").preset);
        assertEquals("python",OfflineFallback.match("Create a Python starter project").preset);
        assertEquals("node",OfflineFallback.match("ابن مشروع Node.js").preset);
    }
    @Test public void neverClaimsToHandleArbitraryTasks(){
        for(String goal:new String[]{"أصلح حسابي البنكي","احذف الصور","افتح واتساب","اشتر سيارة",
            "Build a complex 3D MMORPG game","make coffee","أريد حاسبة HTML لكن لا تكتب كوداً"}){
            // Refusal/unsupported results must remain unambiguous.
            assertNull(goal,OfflineFallback.match(goal));
        }
        assertNull(OfflineFallback.match(null));
    }
}
