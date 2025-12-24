package dev.aegistick.paper.gui;

import dev.aegistick.core.AegisTickCore;
import dev.aegistick.core.config.AegisConfig;
import dev.aegistick.core.optimization.OptimizationType;
import dev.aegistick.paper.AegisTickPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Chest-style admin GUI for Paper servers.
 * Provides visual controls for all AegisTick settings.
 */
public class AdminGui implements Listener {

    private final AegisTickCore core;
    private final AegisTickPlugin plugin;
    private final Set<UUID> openGuis = new HashSet<>();

    private static final String GUI_TITLE = "§6AegisTick Admin";

    public AdminGui(AegisTickCore core, AegisTickPlugin plugin) {
        this.core = core;
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);
        
        // Row 1: Status and main controls
        gui.setItem(4, createStatusItem());
        gui.setItem(0, createToggleItem("§6Global Toggle", core.isEnabled(), Material.LEVER));
        gui.setItem(8, createProfileItem());

        // Row 2: Feature toggles
        AegisConfig config = core.getConfig();
        gui.setItem(19, createFeatureToggle("§eHopper Throttling", config.isHopperThrottlingEnabled(), Material.HOPPER));
        gui.setItem(20, createFeatureToggle("§eEntity AI Sleep", config.isEntityAiSleepEnabled(), Material.ZOMBIE_HEAD));
        gui.setItem(21, createFeatureToggle("§eRandom Tick Scaling", config.isRandomTickScalingEnabled(), Material.WHEAT));
        gui.setItem(22, createFeatureToggle("§eRedstone Delay", config.isRedstoneDelayEnabled(), Material.REDSTONE));
        gui.setItem(23, createFeatureToggle("§eItem Merge", config.isItemMergeEnabled(), Material.CHEST));
        gui.setItem(24, createFeatureToggle("§eLearning Engine", config.isLearningEnabled(), Material.BOOK));

        // Row 3: Statistics
        gui.setItem(37, createStatItem("§bProfiled Chunks", String.valueOf(core.getChunkProfiler().getProfiledChunkCount()), Material.MAP));
        
        var stats = core.getOptimizationEngine().getStats();
        gui.setItem(38, createStatItem("§bActive Optimizations", String.valueOf(stats.totalActiveOptimizations()), Material.DIAMOND));
        gui.setItem(39, createStatItem("§bChunks Optimized", String.valueOf(stats.chunksOptimized()), Material.EMERALD));

        var learning = core.getLearningEngine().getStats();
        gui.setItem(40, createStatItem("§bRecent TPS", String.format("%.2f", learning.recentAverageTps()), Material.CLOCK));
        gui.setItem(41, createStatItem("§bHigh Stress Chunks", String.valueOf(learning.highStressChunks()), Material.TNT));

        // Row 4: Emergency controls
        gui.setItem(49, createEmergencyItem());

        // Fill empty slots with glass
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        
        for (int i = 0; i < 54; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }

        player.openInventory(gui);
        openGuis.add(player.getUniqueId());
    }

    private ItemStack createStatusItem() {
        Material mat = core.isEmergencyMode() ? Material.REDSTONE_BLOCK : 
                       core.isEnabled() ? Material.EMERALD_BLOCK : Material.COAL_BLOCK;
        
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6AegisTick Status");
        
        List<String> lore = new ArrayList<>();
        lore.add(core.isEnabled() ? "§aEnabled" : "§cDisabled");
        if (core.isEmergencyMode()) {
            lore.add("§c⚠ EMERGENCY MODE ACTIVE");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createToggleItem(String name, boolean enabled, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(
            enabled ? "§aENABLED" : "§cDISABLED",
            "§7Click to toggle"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createProfileItem() {
        AegisConfig.ServerProfile profile = core.getConfig().getServerProfile();
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Server Profile");
        meta.setLore(List.of(
            "§7Current: §f" + profile.name(),
            "§7Click to cycle profiles"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFeatureToggle(String name, boolean enabled, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(
            enabled ? "§aENABLED" : "§cDISABLED",
            "§7Click to toggle"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStatItem(String name, String value, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of("§f" + value));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmergencyItem() {
        boolean emergency = core.isEmergencyMode();
        ItemStack item = new ItemStack(emergency ? Material.BARRIER : Material.STRUCTURE_VOID);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(emergency ? "§c⚠ EMERGENCY MODE" : "§7Emergency Controls");
        meta.setLore(List.of(
            emergency ? "§cClick to force exit" : "§7Emergency mode inactive"
        ));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        AegisConfig config = core.getConfig();

        switch (slot) {
            case 0 -> { // Global toggle
                core.setEnabled(!core.isEnabled());
                open(player);
            }
            case 8 -> { // Profile cycle
                AegisConfig.ServerProfile[] profiles = AegisConfig.ServerProfile.values();
                int current = config.getServerProfile().ordinal();
                config.setServerProfile(profiles[(current + 1) % profiles.length]);
                open(player);
            }
            case 19 -> { // Hopper throttling
                config.setHopperThrottlingEnabled(!config.isHopperThrottlingEnabled());
                open(player);
            }
            case 20 -> { // Entity AI sleep
                config.setEntityAiSleepEnabled(!config.isEntityAiSleepEnabled());
                open(player);
            }
            case 21 -> { // Random tick scaling
                config.setRandomTickScalingEnabled(!config.isRandomTickScalingEnabled());
                open(player);
            }
            case 22 -> { // Redstone delay
                config.setRedstoneDelayEnabled(!config.isRedstoneDelayEnabled());
                open(player);
            }
            case 23 -> { // Item merge
                config.setItemMergeEnabled(!config.isItemMergeEnabled());
                open(player);
            }
            case 24 -> { // Learning engine
                config.setLearningEnabled(!config.isLearningEnabled());
                open(player);
            }
            case 49 -> { // Emergency controls
                if (core.isEmergencyMode()) {
                    // Force exit would go here
                    open(player);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openGuis.remove(event.getPlayer().getUniqueId());
    }
}
