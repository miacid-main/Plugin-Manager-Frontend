package com.chatcleaner;

import com.chatcleaner.commands.ChatCleanerCommand;
import com.chatcleaner.config.ConfigManager;
import com.chatcleaner.listeners.ChatListener;
import com.chatcleaner.managers.CooldownManager;
import com.chatcleaner.managers.FilterManager;
import com.chatcleaner.utils.ServiceLink;
import org.bukkit.plugin.java.JavaPlugin;

public class ChatCleaner extends JavaPlugin {

    private ConfigManager configManager;
    private FilterManager filterManager;
    private CooldownManager cooldownManager;
    private ServiceLink serviceLink;

    @Override
    public void onEnable() {
        // 1. Config
        this.configManager = new ConfigManager(this);

        // 2. Managers
        this.filterManager = new FilterManager(this);
        this.cooldownManager = new CooldownManager(this);

        // 3. Service Link (Backend Communication)
        this.serviceLink = new ServiceLink(this);
        
        // 4. Logging Hook
        getLogger().addHandler(new com.chatcleaner.utils.ConsoleLogger(this));
        
        // 5. Listeners
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        // 6. Commands
        ChatCleanerCommand cmd = new ChatCleanerCommand(this);
        getCommand("chatcleaner").setExecutor(cmd);
        getCommand("chatcleaner").setTabCompleter(cmd);

        getLogger().info("ChatCleaner enabled.");
    }

    @Override
    public void onDisable() {
        if (serviceLink != null) {
            serviceLink.shutdown();
        }
        getLogger().info("ChatCleaner disabled.");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public FilterManager getFilterManager() { return filterManager; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public ServiceLink getServiceLink() { return serviceLink; }
}
