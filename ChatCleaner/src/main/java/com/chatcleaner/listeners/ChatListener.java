package com.chatcleaner.listeners;

import com.chatcleaner.ChatCleaner;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatListener implements Listener {

    private final ChatCleaner plugin;

    public ChatListener(ChatCleaner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.getServiceLink().updatePlayers();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getServiceLink().updatePlayers();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        // Removed default bypass check so OPs are also filtered for testing
        // if (p.hasPermission("chatcleaner.bypass")) return;

        // 1. Slow Mode
        if (plugin.getCooldownManager().isOnCooldown(p.getUniqueId())) {
            e.setCancelled(true);
            long left = plugin.getCooldownManager().getRemainingCooldown(p.getUniqueId());
            p.sendMessage("§ePlease wait §6" + left + "s §ebefore sending another message.");
            return;
        }

        // 2. Spam Prevention
        if (plugin.getCooldownManager().isSpamming(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage("§cPlease do not spam.");
            plugin.getServiceLink().logEvent("spam_block", String.format("{\"player\":\"%s\",\"type\":\"fast_typing\"}", p.getName()));
            return;
        }

        plugin.getCooldownManager().updateLastMessage(p.getUniqueId());

        String message = e.getMessage();

        // 3. Caps Filter
        if (plugin.getFilterManager().isCapsSpam(message)) {
            message = message.toLowerCase();
            e.setMessage(message);
        }

        // 4. Word Filter
        String filtered = plugin.getFilterManager().filter(message);
        if (!filtered.equals(message)) {
            e.setMessage(filtered);
            p.sendMessage("§cYour message contained prohibited content and was modified.");
            
            // Log locally
            if (plugin.getConfigManager().isLogFiltered()) {
                plugin.getLogger().info("Filtered message from " + p.getName() + ": " + message);
            }
            
            // Log to Backend
            String details = String.format("{\"player\":\"%s\",\"original\":\"%s\",\"filtered\":\"%s\"}", 
                p.getName(), message, filtered);
            plugin.getServiceLink().logEvent("filter_trigger", details);
        }
    }
}
