package dev.aegistick.paper;

import dev.aegistick.core.AegisTickCore;
import dev.aegistick.core.config.AegisConfig;
import dev.aegistick.paper.adapter.PaperPlatformAdapter;
import dev.aegistick.paper.command.AegisTickCommand;
import dev.aegistick.paper.listener.ChunkListener;
import dev.aegistick.paper.listener.EntityListener;
import dev.aegistick.paper.listener.PlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper/Bukkit entry point for AegisTick.
 * Initializes platform adapters and registers events.
 */
public class AegisTickPlugin extends JavaPlugin {

    private AegisTickCore core;
    private PaperPlatformAdapter platformAdapter;

    @Override
    public void onEnable() {
        // Initialize configuration
        saveDefaultConfig();
        AegisConfig config = loadConfig();

        // Create platform adapter
        platformAdapter = new PaperPlatformAdapter(this);

        // Initialize core
        core = new AegisTickCore(platformAdapter, config);

        // Register event listeners
        getServer().getPluginManager().registerEvents(new ChunkListener(core), this);
        getServer().getPluginManager().registerEvents(new EntityListener(core), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(core, platformAdapter), this);

        // Register command
        getCommand("aegistick").setExecutor(new AegisTickCommand(core, this));

        // Start tick loop
        platformAdapter.scheduleRepeating(core::onTick, 1L, 1L);

        getLogger().info("AegisTick enabled - invisible optimization active");
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.shutdown();
        }
        getLogger().info("AegisTick disabled - all optimizations reverted");
    }

    private AegisConfig loadConfig() {
        AegisConfig config = new AegisConfig();
        
        // Load from config.yml
        config.setCriticalTpsThreshold(getConfig().getDouble("tps.critical-threshold", 14.0));
        config.setWarningTpsThreshold(getConfig().getDouble("tps.warning-threshold", 17.0));
        config.setTargetTps(getConfig().getDouble("tps.target", 20.0));
        
        config.setBaseAggressiveness(getConfig().getDouble("optimization.base-aggressiveness", 0.5));
        config.setEmergencyAggressiveness(getConfig().getDouble("optimization.emergency-aggressiveness", 0.9));
        
        config.setHopperThrottlingEnabled(getConfig().getBoolean("features.hopper-throttling", true));
        config.setEntityAiSleepEnabled(getConfig().getBoolean("features.entity-ai-sleep", true));
        config.setRandomTickScalingEnabled(getConfig().getBoolean("features.random-tick-scaling", true));
        config.setRedstoneDelayEnabled(getConfig().getBoolean("features.redstone-delay", true));
        config.setItemMergeEnabled(getConfig().getBoolean("features.item-merge", true));
        
        config.setBuilderProtectionRadius(getConfig().getInt("safety.builder-protection-radius", 32));
        config.setCombatProtectionDuration(getConfig().getInt("safety.combat-protection-ticks", 100));
        config.setAfkThresholdTicks(getConfig().getInt("safety.afk-threshold-ticks", 6000));
        
        config.setLearningEnabled(getConfig().getBoolean("learning.enabled", true));
        
        String profile = getConfig().getString("profile", "SMP");
        config.setServerProfile(AegisConfig.ServerProfile.valueOf(profile.toUpperCase()));
        
        return config;
    }

    public AegisTickCore getCore() {
        return core;
    }

    public void reloadConfiguration() {
        reloadConfig();
        AegisConfig newConfig = loadConfig();
        // Update config in core (config object is shared by reference)
    }
}
