package com.chatcleaner.managers;

import com.chatcleaner.ChatCleaner;
import java.util.regex.Pattern;

public class FilterManager {

    private final ChatCleaner plugin;

    public FilterManager(ChatCleaner plugin) {
        this.plugin = plugin;
    }

    public String filter(String message) {
        String filtered = message;
        for (String word : plugin.getConfigManager().getBannedWords()) {
            if (plugin.getConfigManager().getWhitelist().contains(word.toLowerCase())) continue;
            
            // Improved regex to handle case insensitivity and partial matches better
            // (?i) enables case insensitivity
            // We use Pattern.quote to handle special characters in the banned word
            filtered = filtered.replaceAll("(?i)\\b" + Pattern.quote(word) + "\\b", plugin.getConfigManager().getReplacement());
            
            // Also check for direct inclusion without word boundaries if it's a severe word?
            // For now, let's try strict word boundaries first to avoid "ass" in "glass"
            // But if the user wants strict filtering, maybe we remove \\b for some words.
            // The previous code had NO word boundaries, which causes "clASS" issues.
            // But the user said "its not filtering", implying it didn't catch the bad word.
            // If they typed "cunt" and it wasn't caught, the previous code SHOULD have caught it.
            // UNLESS they were OP.
            
            // Let's go back to the previous logic (contains) but keep the OP fix in Listener.
            // Actually, let's keep it simple: replace all occurrences, ignore case.
            filtered = filtered.replaceAll("(?i)" + Pattern.quote(word), plugin.getConfigManager().getReplacement());
        }
        return filtered;
    }

    public boolean isCapsSpam(String message) {
        if (!plugin.getConfigManager().isCapsLockFilter()) return false;
        if (message.length() < 5) return false;

        int caps = 0;
        for (char c : message.toCharArray()) {
            if (Character.isUpperCase(c)) caps++;
        }

        double percent = (double) caps / message.length() * 100;
        return percent >= plugin.getConfigManager().getCapsThreshold();
    }
}
