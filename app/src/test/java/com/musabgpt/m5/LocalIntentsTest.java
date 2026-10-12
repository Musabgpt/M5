package com.musabgpt.m5;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocalIntentsTest {
    @Test public void arabicAndEnglishLaunchesAreDirect() {
        assertEquals("واتساب",LocalIntents.appToOpen("افتح واتساب"));
        assertEquals("يوتيوب",LocalIntents.appToOpen("شغل يوتيوب"));
        assertEquals("WhatsApp",LocalIntents.appToOpen("open WhatsApp"));
        assertEquals("Google Chrome",LocalIntents.appToOpen("launch Google Chrome"));
    }
    @Test public void multiStepOrOrdinaryChatDoesNotPretendToBeOneTap() {
        assertNull(LocalIntents.appToOpen("افتح واتساب ثم ابعث رسالة"));
        assertNull(LocalIntents.appToOpen("مرحبا"));
        assertNull(LocalIntents.appToOpen("أريد برنامج حاسبة"));
        assertNull(LocalIntents.appToOpen("افتح https://example.com"));
    }
}
