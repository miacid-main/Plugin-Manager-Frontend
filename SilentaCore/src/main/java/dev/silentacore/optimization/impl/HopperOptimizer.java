package dev.silentacore.optimization.impl;

import dev.silentacore.SilentaCore;
import dev.silentacore.optimization.Optimizer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HopperOptimizer implements Optimizer, Listener {

    private final SilentaCore plugin;
    private final Map<Integer, Long> hopperCooldowns = new HashMap<>();

    public HopperOptimizer(SilentaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        hopperCooldowns.clear();
    }

    @Override
    public void tick() {
        // Cleanup cache periodically
        if (Bukkit.getCurrentTick() % 1200 == 0) {
            hopperCooldowns.clear();
        }
    }

    @Override
    public String getName() {
        return "Adaptive Hopper Throttling";
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent e) {
        if (shouldThrottle(e.getSource().getLocation().hashCode())) {
            e.setCancelled(true);
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (shouldThrottle(e.getInventory().getLocation().hashCode())) {
            e.setCancelled(true);
        }
    }

    private boolean shouldThrottle(int hopperId) {
        // Check TPS levels
        int throttleTicks = 0;
        if (plugin.getPerformanceMonitor().isLevel3()) {
            throttleTicks = plugin.getConfigManager().getInt("hopper-optimization.throttle-delay.level3", 40);
        } else if (plugin.getPerformanceMonitor().isLevel2()) {
            throttleTicks = plugin.getConfigManager().getInt("hopper-optimization.throttle-delay.level2", 20);
        } else if (plugin.getPerformanceMonitor().isLevel1()) {
            throttleTicks = plugin.getConfigManager().getInt("hopper-optimization.throttle-delay.level1", 10);
        }

        if (throttleTicks <= 0) return false;

        long now = Bukkit.getCurrentTick(); // Use tick-based timing for consistency with config
        long lastTransfer = hopperCooldowns.getOrDefault(hopperId, 0L);
        
        // If "throttleTicks" have not passed since last transfer, CANCEL this one.
        if (now - lastTransfer < throttleTicks) {
            return true;
        }
        
        // Otherwise, allow it and update timestamp
        hopperCooldowns.put(hopperId, now);
        return false;
    }
}
