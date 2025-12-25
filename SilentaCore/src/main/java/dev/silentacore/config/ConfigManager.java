package dev.silentacore.config;

import dev.silentacore.SilentaCore;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final SilentaCore plugin;
    private FileConfiguration config;

    public ConfigManager(SilentaCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public boolean isDebug() {
        return config.getBoolean("debug", false);
    }

    public double getLevel1Tps() {
        return config.getDouble("monitoring.level1-tps", 19.5);
    }

    public double getLevel2Tps() {
        return config.getDouble("monitoring.level2-tps", 18.0);
    }

    public double getLevel3Tps() {
        return config.getDouble("monitoring.level3-tps", 16.0);
    }

    // Generic getters
    public boolean getBoolean(String path) {
        return config.getBoolean(path);
    }

    public int getInt(String path) {
        return config.getInt(path);
    }
    
    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public double getDouble(String path) {
        return config.getDouble(path);
    }
    
    public java.util.List<String> getStringList(String path) {
        return config.getStringList(path);
    }
}
