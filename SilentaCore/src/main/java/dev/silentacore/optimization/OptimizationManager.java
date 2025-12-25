package dev.silentacore.optimization;

import dev.silentacore.SilentaCore;
import dev.silentacore.optimization.impl.*;
import dev.silentacore.protection.AfkManager;
import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class OptimizationManager {

    private final SilentaCore plugin;
    // Key: Config path key (e.g. "hopper-optimization") -> Optimizer
    private final Map<String, Optimizer> optimizers = new HashMap<>();
    private final Map<String, Boolean> activeState = new HashMap<>();

    public OptimizationManager(SilentaCore plugin) {
        this.plugin = plugin;
        
        // Register All Optimizers (regardless of config state initially)
        register("hopper-optimization", new HopperOptimizer(plugin));
        register("entity-ai-optimization", new EntityAIOptimizer(plugin));
        register("random-tick-optimization", new RandomTickOptimizer(plugin));
        register("redstone-optimization", new RedstoneOptimizer(plugin));
        register("item-merge-optimization", new ItemMergeOptimizer(plugin));
        register("afk-optimization", new AfkManager(plugin));
    }
    
    private void register(String configKey, Optimizer optimizer) {
        optimizers.put(configKey, optimizer);
    }

    public void enableOptimizations() {
        for (Map.Entry<String, Optimizer> entry : optimizers.entrySet()) {
            String key = entry.getKey();
            Optimizer opt = entry.getValue();
            
            // Check config
            boolean enabled = plugin.getConfigManager().getBoolean(key + ".enabled");
            if (enabled) {
                startOptimizer(key, opt);
            } else {
                activeState.put(key, false);
            }
        }

        // Global tick task for optimizers
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<String, Optimizer> entry : optimizers.entrySet()) {
                if (activeState.getOrDefault(entry.getKey(), false)) {
                    entry.getValue().tick();
                }
            }
        }, 20L, 20L);
    }

    public void disableOptimizations() {
        for (Map.Entry<String, Optimizer> entry : optimizers.entrySet()) {
            stopOptimizer(entry.getKey(), entry.getValue());
        }
    }
    
    // Runtime Toggling
    public void toggle(String configKey, boolean enable) {
        Optimizer opt = optimizers.get(configKey);
        if (opt == null) return;
        
        if (enable) {
            startOptimizer(configKey, opt);
        } else {
            stopOptimizer(configKey, opt);
        }
        
        // Update Config
        plugin.getConfig().set(configKey + ".enabled", enable);
        plugin.saveConfig();
        plugin.getConfigManager().reload(); // Reload manager to sync cache
    }
    
    public boolean isEnabled(String configKey) {
        return activeState.getOrDefault(configKey, false);
    }
    
    public Collection<String> getKeys() {
        return optimizers.keySet();
    }
    
    public Optimizer getOptimizer(String key) {
        return optimizers.get(key);
    }

    private void startOptimizer(String key, Optimizer opt) {
        if (activeState.getOrDefault(key, false)) return; // Already enabled
        try {
            opt.enable();
            activeState.put(key, true);
            plugin.getLogger().info("Enabled optimizer: " + opt.getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to enable optimizer " + opt.getName() + ": " + e.getMessage());
            activeState.put(key, false);
        }
    }
    
    private void stopOptimizer(String key, Optimizer opt) {
        if (!activeState.getOrDefault(key, false)) return; // Already disabled
        try {
            opt.disable();
            activeState.put(key, false);
            plugin.getLogger().info("Disabled optimizer: " + opt.getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to disable optimizer " + opt.getName() + ": " + e.getMessage());
        }
    }
}
