package com.musabgpt.m5;
import org.junit.Test;
import static org.junit.Assert.*;
public class ShellConsentTest {
    @Test public void ordinaryReadOnlyCommandsNeedNoExtraConsent(){
        assertEquals(ShellConsent.Decision.SAFE_READ,ShellConsent.classify("pwd"));
        assertEquals(ShellConsent.Decision.SAFE_READ,ShellConsent.classify("git status"));
        assertEquals(ShellConsent.Decision.SAFE_READ,ShellConsent.classify("node --version"));
    }
    @Test public void buildsAndInstallersNeedSingleStepApproval(){
        assertEquals(ShellConsent.Decision.APPROVE_ONCE,ShellConsent.classify("python -m pytest -q"));
        assertEquals(ShellConsent.Decision.APPROVE_ONCE,ShellConsent.classify("npm install --save-dev typescript"));
        assertEquals(ShellConsent.Decision.APPROVE_ONCE,ShellConsent.classify("./gradlew assembleDebug"));
    }
    @Test public void rootAndDestructiveOperationsAreRejected(){
        for(String command:new String[]{"sudo id","su -c id","rm -rf /","git push --force origin main",
            "termux-setup-storage","mkfs.ext4 /dev/block/foo","reboot"}){
            assertEquals(command,ShellConsent.Decision.REJECT,ShellConsent.classify(command));
        }
    }
}
