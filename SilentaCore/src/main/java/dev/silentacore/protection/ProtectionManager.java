package dev.silentacore.protection;

import dev.silentacore.SilentaCore;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectionManager implements Listener {

    private final SilentaCore plugin;
    
    // Chunk Hash -> Timestamp (ms) when protection expires
    private final Map<Long, Long> builderProtection = new ConcurrentHashMap<>();
    
    // Entity UUID -> Timestamp (ms) when protection expires
    private final Map<UUID, Long> combatProtection = new ConcurrentHashMap<>();
    
    // Player UUID -> Timestamp (ms) of last activity
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    
    private final long builderExemptionMs;
    private final long combatExemptionMs;
    private final long afkTimeoutMs;

    public ProtectionManager(SilentaCore plugin) {
        this.plugin = plugin;
        this.builderExemptionMs = plugin.getConfigManager().getInt("builder-protection.exemption-duration", 30) * 1000L;
        this.combatExemptionMs = plugin.getConfigManager().getInt("combat-protection.exemption-duration", 15) * 1000L;
        this.afkTimeoutMs = plugin.getConfigManager().getInt("afk-optimization.afk-timeout", 300) * 1000L;
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Cleanup task
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup, 1200L, 1200L);
    }
    
    private void cleanup() {
        long now = System.currentTimeMillis();
        builderProtection.entrySet().removeIf(e -> e.getValue() < now);
        combatProtection.entrySet().removeIf(e -> e.getValue() < now);
        lastActivity.entrySet().removeIf(e -> Bukkit.getPlayer(e.getKey()) == null);
    }

    // --- API ---

    public boolean isChunkProtected(Chunk chunk) {
        if (!plugin.getConfigManager().getBoolean("builder-protection.enabled")) return false;
        long key = getChunkKey(chunk);
        return builderProtection.getOrDefault(key, 0L) > System.currentTimeMillis();
    }

    public boolean isEntityProtected(Entity entity) {
        if (!plugin.getConfigManager().getBoolean("combat-protection.enabled")) return false;
        return combatProtection.getOrDefault(entity.getUniqueId(), 0L) > System.currentTimeMillis();
    }
    
    public boolean isPlayerAfk(Player player) {
        if (!plugin.getConfigManager().getBoolean("afk-optimization.enabled")) return false;
        long last = lastActivity.getOrDefault(player.getUniqueId(), System.currentTimeMillis());
        return (System.currentTimeMillis() - last) > afkTimeoutMs;
    }

    // --- Listeners ---

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        triggerBuilderProtection(e.getBlock().getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        triggerBuilderProtection(e.getBlock().getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent e) {
        triggerCombatProtection(e.getEntity());
        triggerCombatProtection(e.getDamager());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() != e.getTo().getBlockX() || 
            e.getFrom().getBlockZ() != e.getTo().getBlockZ() || 
            e.getFrom().getBlockY() != e.getTo().getBlockY()) {
            lastActivity.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }
    
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        lastActivity.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }
    
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastActivity.remove(e.getPlayer().getUniqueId());
        combatProtection.remove(e.getPlayer().getUniqueId());
    }

    // --- Helpers ---

    private void triggerBuilderProtection(Chunk chunk) {
        long key = getChunkKey(chunk);
        builderProtection.put(key, System.currentTimeMillis() + builderExemptionMs);
    }

    private void triggerCombatProtection(Entity entity) {
        combatProtection.put(entity.getUniqueId(), System.currentTimeMillis() + combatExemptionMs);
    }

    private long getChunkKey(Chunk chunk) {
        return (long) chunk.getX() & 0xffffffffL | ((long) chunk.getZ() & 0xffffffffL) << 32;
    }
}
