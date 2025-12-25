package com.chatcleaner.managers;

import com.chatcleaner.ChatCleaner;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final ChatCleaner plugin;
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();

    public CooldownManager(ChatCleaner plugin) {
        this.plugin = plugin;
    }

    public boolean isOnCooldown(UUID uuid) {
        if (!plugin.getConfigManager().isSlowModeEnabled()) return false;
        
        long now = System.currentTimeMillis();
        long last = lastMessageTime.getOrDefault(uuid, 0L);
        long delay = plugin.getConfigManager().getSlowModeDelay() * 1000L;
        
        return (now - last) < delay;
    }

    public boolean isSpamming(UUID uuid) {
        long now = System.currentTimeMillis();
        long last = lastMessageTime.getOrDefault(uuid, 0L);
        long delay = plugin.getConfigManager().getMessageDelaySeconds() * 1000L;
        
        return (now - last) < delay;
    }

    public void updateLastMessage(UUID uuid) {
        lastMessageTime.put(uuid, System.currentTimeMillis());
    }

    public long getRemainingCooldown(UUID uuid) {
        long now = System.currentTimeMillis();
        long last = lastMessageTime.getOrDefault(uuid, 0L);
        long delay = plugin.getConfigManager().getSlowModeDelay() * 1000L;
        return (delay - (now - last)) / 1000;
    }
}
