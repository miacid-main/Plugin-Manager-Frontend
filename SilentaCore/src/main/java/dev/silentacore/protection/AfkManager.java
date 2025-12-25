package dev.silentacore.protection;

import dev.silentacore.SilentaCore;
import dev.silentacore.optimization.Optimizer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class AfkManager implements Optimizer {

    private final SilentaCore plugin;
    private boolean reduceViewDistance;

    public AfkManager(SilentaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        this.reduceViewDistance = plugin.getConfigManager().getBoolean("afk-optimization.reduce-view-distance");
    }

    @Override
    public void disable() {
        // Restore view distance
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setViewDistance(Bukkit.getServer().getViewDistance());
            p.setSimulationDistance(Bukkit.getServer().getSimulationDistance());
        }
    }

    @Override
    public void tick() {
        if (!plugin.getConfigManager().getBoolean("afk-optimization.enabled")) return;
        if (Bukkit.getCurrentTick() % 100 != 0) return; // Check every 5s

        int defaultView = Bukkit.getServer().getViewDistance();
        int defaultSim = Bukkit.getServer().getSimulationDistance();

        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean isAfk = plugin.getProtectionManager().isPlayerAfk(p);
            
            if (isAfk && reduceViewDistance) {
                // Set to minimum safe values
                if (p.getViewDistance() > 4) p.setViewDistance(4);
                if (p.getSimulationDistance() > 4) p.setSimulationDistance(4);
            } else {
                // Restore if active
                if (p.getViewDistance() != defaultView) p.setViewDistance(defaultView);
                if (p.getSimulationDistance() != defaultSim) p.setSimulationDistance(defaultSim);
            }
        }
    }

    @Override
    public String getName() {
        return "AFK Awareness Optimization";
    }
}
