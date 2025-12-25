package dev.silentacore.monitor;

import dev.silentacore.SilentaCore;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public class PerformanceMonitor implements Runnable {

    private final SilentaCore plugin;
    private double currentTps = 20.0;
    private long lastTickTime = 0;
    private BukkitTask task;

    public PerformanceMonitor(SilentaCore plugin) {
        this.plugin = plugin;
    }

    public void startMonitoring() {
        // Run every tick to measure exact tick duration if needed, 
        // but for general TPS, Bukkit's internal tracker is often enough.
        // However, to be "TPS-aware" instantly, we can check Bukkit.getTPS()[0] (1 minute average)
        // or calculate our own instantaneous TPS.
        
        // Let's use a task that runs every 20 ticks (1 second) to update our internal state.
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this, 20L, 20L);
    }

    @Override
    public void run() {
        // Get 1-minute average TPS from Paper API
        double[] tps = Bukkit.getTPS();
        this.currentTps = tps.length > 0 ? tps[0] : 20.0;
        
        if (currentTps > 20.0) currentTps = 20.0; // Cap visual artifacts
    }

    public double getCurrentTps() {
        return currentTps;
    }

    public boolean isLevel1() {
        return currentTps < plugin.getConfigManager().getLevel1Tps();
    }

    public boolean isLevel2() {
        return currentTps < plugin.getConfigManager().getLevel2Tps();
    }

    public boolean isLevel3() {
        return currentTps < plugin.getConfigManager().getLevel3Tps();
    }
}
