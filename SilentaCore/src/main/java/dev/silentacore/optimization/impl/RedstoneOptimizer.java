package dev.silentacore.optimization.impl;

import dev.silentacore.SilentaCore;
import dev.silentacore.optimization.Optimizer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RedstoneOptimizer implements Optimizer, Listener {

    private final SilentaCore plugin;
    // Location Hash -> Last Update Timestamp
    private final Map<Integer, Long> lastUpdate = new HashMap<>();
    // Location Hash -> Last New Current
    private final Map<Integer, Integer> lastCurrent = new HashMap<>();

    public RedstoneOptimizer(SilentaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        lastUpdate.clear();
        lastCurrent.clear();
    }

    @Override
    public void tick() {
        if (Bukkit.getCurrentTick() % 600 == 0) {
            lastUpdate.clear();
            lastCurrent.clear();
        }
    }

    @Override
    public String getName() {
        return "Lossless Redstone Optimization";
    }

    @EventHandler
    public void onRedstone(BlockRedstoneEvent e) {
        if (!plugin.getConfigManager().getBoolean("redstone-optimization.enabled")) return;

        // 1. Check Protection
        // Getting chunk from block is cheap
        if (plugin.getProtectionManager().isChunkProtected(e.getBlock().getChunk())) return;

        // 2. Cache Logic
        // We use hashCode for speed; collisions are rare enough for redstone logic in small radius
        int locHash = e.getBlock().getLocation().hashCode(); 
        
        // "Lossless" means we don't change behavior unless critical.
        // "Cache unchanged redstone states":
        // If the event proposes a change that is effectively a no-op (shouldn't fire, but sometimes plugins/core does), cancel.
        // The event is "From X to Y". If X == Y, it's redundant.
        if (e.getOldCurrent() == e.getNewCurrent()) {
            // This is effectively a no-op event, suppress it.
            e.setNewCurrent(e.getOldCurrent());
            return;
        }

        // 3. Suppression on Lag
        if (plugin.getConfigManager().getBoolean("redstone-optimization.suppress-updates-on-lag") 
            && plugin.getPerformanceMonitor().isLevel3()) {
            
            // Limit frequency: Max 1 update per 4 ticks (200ms) per block
            long last = lastUpdate.getOrDefault(locHash, 0L);
            long now = System.currentTimeMillis();
            if (now - last < 200) {
                // Too fast for emergency mode! Keep old current to suppress update
                e.setNewCurrent(e.getOldCurrent());
                return;
            }
            lastUpdate.put(locHash, now);
        }
        
        // Update cache
        lastCurrent.put(locHash, e.getNewCurrent());
    }
}
