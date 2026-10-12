package com.musabgpt.m5;
import org.junit.Test;
import static org.junit.Assert.*;

public class DraftRecoveryPolicyTest {
    @Test public void noStoredDraft() {
        assertEquals(DraftRecoveryPolicy.Decision.NO_DRAFT,
            DraftRecoveryPolicy.evaluate(null,null,"file from disk","abc"));
    }
    @Test public void redundantDraftCanBeRemoved() {
        assertEquals(DraftRecoveryPolicy.Decision.DISCARD_REDUNDANT,
            DraftRecoveryPolicy.evaluate("same text","old-hash","same text","new-hash"));
    }
    @Test public void newerUnsavedTextMayBeRestoredWithoutDiskChange() {
        assertEquals(DraftRecoveryPolicy.Decision.RESTORE,
            DraftRecoveryPolicy.evaluate("unsaved code","hash-1","saved code","hash-1"));
    }
    @Test public void diskConflictMustRequireUserDecision() {
        assertEquals(DraftRecoveryPolicy.Decision.CONFLICT,
            DraftRecoveryPolicy.evaluate("draft","hash-1","externally edited","hash-2"));
        assertEquals(DraftRecoveryPolicy.Decision.CONFLICT,
            DraftRecoveryPolicy.evaluate("draft","", "new disk file","hash-2"));
    }
}
