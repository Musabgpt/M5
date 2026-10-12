package com.musabgpt.m5;

/** Pure, deterministic policy for restoring unsaved work without silent disk replacement. */
public final class DraftRecoveryPolicy {
    private DraftRecoveryPolicy() {}
    public enum Decision { NO_DRAFT, DISCARD_REDUNDANT, RESTORE, CONFLICT }
    public static Decision evaluate(String draftContents,String baseHash,String diskContents,String diskHash){
        if(draftContents==null)return Decision.NO_DRAFT;
        if(draftContents.equals(diskContents))return Decision.DISCARD_REDUNDANT;
        return baseHash!=null&&baseHash.equals(diskHash)?Decision.RESTORE:Decision.CONFLICT;
    }
}
