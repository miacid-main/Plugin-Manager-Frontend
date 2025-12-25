package dev.silentacore;

import dev.silentacore.backend.BackendClient;
import dev.silentacore.command.SilentaCommand;
import dev.silentacore.config.ConfigManager;
import dev.silentacore.gui.GuiManager;
import dev.silentacore.monitor.PerformanceMonitor;
import dev.silentacore.optimization.OptimizationManager;
import dev.silentacore.protection.ProtectionManager;
import org.bukkit.plugin.java.JavaPlugin;

public class SilentaCore extends JavaPlugin {

    private static SilentaCore instance;
    private ConfigManager configManager;
    private PerformanceMonitor performanceMonitor;
    private OptimizationManager optimizationManager;
    private ProtectionManager protectionManager;
    private BackendClient backendClient;
    private GuiManager guiManager;

    public static SilentaCore getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // 1. Load Config
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);

        // 2. Initialize Managers
        this.performanceMonitor = new PerformanceMonitor(this);
        this.protectionManager = new ProtectionManager(this);
        this.optimizationManager = new OptimizationManager(this);
        this.guiManager = new GuiManager(this);
        this.backendClient = new BackendClient(this);

        // 3. Register Commands
        SilentaCommand cmd = new SilentaCommand(this);
        getCommand("silentacore").setExecutor(cmd);
        getCommand("silentacore").setTabCompleter(cmd);

        // 4. Start Tasks
        this.performanceMonitor.startMonitoring();
        this.optimizationManager.enableOptimizations();
        this.backendClient.start();

        getLogger().info("SilentaCore has been enabled. Optimization engine: ACTIVE.");
    }

    @Override
    public void onDisable() {
        if (this.backendClient != null) {
            this.backendClient.shutdown();
        }
        if (this.optimizationManager != null) {
            this.optimizationManager.disableOptimizations();
        }
        
        getLogger().info("SilentaCore has been disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    public OptimizationManager getOptimizationManager() {
        return optimizationManager;
    }

    public ProtectionManager getProtectionManager() {
        return protectionManager;
    }

    public BackendClient getBackendClient() {
        return backendClient;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }
}
