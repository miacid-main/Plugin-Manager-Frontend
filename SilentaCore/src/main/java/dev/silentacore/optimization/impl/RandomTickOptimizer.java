package dev.silentacore.optimization.impl;

import dev.silentacore.SilentaCore;
import dev.silentacore.optimization.Optimizer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;

import java.util.concurrent.ThreadLocalRandom;

public class RandomTickOptimizer implements Optimizer, Listener {

    private final SilentaCore plugin;
    private int tileEntityThreshold;

    public RandomTickOptimizer(SilentaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.tileEntityThreshold = plugin.getConfigManager().getInt("random-tick-optimization.tile-entity-threshold", 64);
    }

    @Override
    public void disable() {
        // No cleanup needed for event listener
    }

    @Override
    public void tick() {
        // Event based
    }

    @Override
    public String getName() {
        return "Random Tick Load Scaling";
    }

    @EventHandler(ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        handleRandomTick(e.getBlock().getChunk(), e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        handleRandomTick(e.getBlock().getChunk(), e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        handleRandomTick(e.getBlock().getChunk(), e);
    }

    private void handleRandomTick(Chunk chunk, org.bukkit.event.Cancellable e) {
        // 1. Check TPS
        if (!plugin.getPerformanceMonitor().isLevel2()) return; // Only optimize if TPS < 18

        // 2. Check Builder Protection
        if (plugin.getProtectionManager().isChunkProtected(chunk)) return;

        // 3. Check Chunk Load (Tile Entities)
        // Note: getTileEntities() can be expensive if not cached, but Paper usually handles it well.
        // For micro-optimization, we trust the array length check is fast enough or use a snapshot if available.
        // In 1.21, getTileEntities() might return a copy.
        // A lighter check is checking if the chunk has ANY heavy logic. 
        // We'll stick to the configured threshold.
        int teCount = chunk.getTileEntities().length;
        if (teCount < tileEntityThreshold) return;

        // 4. Calculate Cancel Probability
        // More tile entities + Lower TPS = Higher chance to cancel
        double chance = 0.0;
        
        if (plugin.getPerformanceMonitor().isLevel3()) { // < 16 TPS
            chance = 0.60; // Cancel 60%
        } else { // < 18 TPS
            chance = 0.30; // Cancel 30%
        }
        
        // Scale by TE count? 
        // If TE count is double the threshold, increase chance.
        if (teCount > tileEntityThreshold * 2) {
            chance += 0.20;
        }

        if (ThreadLocalRandom.current().nextDouble() < chance) {
            e.setCancelled(true);
        }
    }
}
