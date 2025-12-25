package com.chatcleaner.config;

import com.chatcleaner.ChatCleaner;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.ArrayList;

public class ConfigManager {
    private final ChatCleaner plugin;
    
    // Config values
    private List<String> bannedWords;
    private String replacement;
    private int messageDelaySeconds;
    private boolean slowModeEnabled;
    private int slowModeDelay;
    private boolean capsLockFilter;
    private int capsThreshold;
    private boolean logFiltered;
    private String logFile;
    private List<String> whitelist;

    public ConfigManager(ChatCleaner plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        this.bannedWords = c.getStringList("banned-words");
        this.replacement = c.getString("replacement", "*");
        this.messageDelaySeconds = c.getInt("message-delay-seconds", 3);
        this.slowModeEnabled = c.getBoolean("slowmode-enabled", true);
        this.slowModeDelay = c.getInt("slowmode-delay", 5);
        this.capsLockFilter = c.getBoolean("caps-lock-filter", true);
        this.capsThreshold = c.getInt("caps-threshold", 50);
        this.logFiltered = c.getBoolean("log-filtered", true);
        this.logFile = c.getString("log-file", "plugins/ChatCleaner/logs/filtered.log");
        this.whitelist = c.getStringList("whitelist");
        
        if (this.whitelist == null) this.whitelist = new ArrayList<>();
        if (this.bannedWords == null) this.bannedWords = new ArrayList<>();
    }

    public List<String> getBannedWords() { return bannedWords; }
    public String getReplacement() { return replacement; }
    public int getMessageDelaySeconds() { return messageDelaySeconds; }
    public boolean isSlowModeEnabled() { return slowModeEnabled; }
    public int getSlowModeDelay() { return slowModeDelay; }
    public boolean isCapsLockFilter() { return capsLockFilter; }
    public int getCapsThreshold() { return capsThreshold; }
    public boolean isLogFiltered() { return logFiltered; }
    public String getLogFile() { return logFile; }
    public List<String> getWhitelist() { return whitelist; }
    
    public void addWhitelist(String word) {
        if (!whitelist.contains(word.toLowerCase())) {
            whitelist.add(word.toLowerCase());
            plugin.getConfig().set("whitelist", whitelist);
            plugin.saveConfig();
        }
    }
    
    public void removeWhitelist(String word) {
        if (whitelist.remove(word.toLowerCase())) {
            plugin.getConfig().set("whitelist", whitelist);
            plugin.saveConfig();
        }
    }
}
