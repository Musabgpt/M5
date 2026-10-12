package com.musabgpt.m5;

import java.util.Locale;

public final class ShellConsent {
    private ShellConsent(){}
    public enum Decision { REJECT, APPROVE_ONCE, SAFE_READ }
    public static Decision classify(String value){
        if(value==null||value.isBlank()||value.length()>4096||value.indexOf('\0')>=0)
            return Decision.REJECT;
        String s=value.trim().toLowerCase(Locale.ROOT);
        if(s.matches("(?s).*(?:^|[ ;|&])(?:sudo|su|setenforce|mkfs(?:\\.[a-z0-9]+)?|reboot|poweroff|shutdown)(?:\\s|$).*")||
           s.matches("(?s).*\\brm\\s+(-[a-z]*r[a-z]*f|-f[a-z]*r|--no-preserve-root).*")||
           s.contains("git push --force")||s.contains("git push -f")||
           s.contains("termux-setup-storage")||s.contains("/proc/sysrq-trigger")||
           s.contains(">/dev/block/")||s.contains("chown -r /"))
            return Decision.REJECT;
        if(s.matches("(?s).*[;|&<>$\\n\\r].*")||s.indexOf(96)>=0)
            return Decision.APPROVE_ONCE;
        if(s.matches("pwd|ls(?:\\s+[-a-z0-9_.\\/ ]{0,100})?|git status(?:\\s+--short)?|git diff(?:\\s+--stat)?|python(?:3)? --version|node --version|npm --version"))
            return Decision.SAFE_READ;
        return Decision.APPROVE_ONCE;
    }
}
