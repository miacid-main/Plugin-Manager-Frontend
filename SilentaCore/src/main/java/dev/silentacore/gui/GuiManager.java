package dev.silentacore.gui;

import dev.silentacore.SilentaCore;
import dev.silentacore.optimization.Optimizer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class GuiManager implements Listener {

    private final SilentaCore plugin;
    private final String GUI_TITLE = ChatColor.DARK_GRAY + "SilentaCore Control Panel";

    public GuiManager(SilentaCore plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, GUI_TITLE);

        // 1. Modules
        // We'll arrange them in a centered pattern (e.g. slots 20, 21, 22, 23, 24)
        int[] moduleSlots = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33}; // Plenty of space
        int index = 0;
        
        for (String key : plugin.getOptimizationManager().getKeys()) {
            if (index >= moduleSlots.length) break;
            
            Optimizer opt = plugin.getOptimizationManager().getOptimizer(key);
            boolean enabled = plugin.getOptimizationManager().isEnabled(key);
            
            gui.setItem(moduleSlots[index++], createModuleItem(opt.getName(), key, enabled));
        }

        // 2. Info Item (Center Top)
        gui.setItem(4, createInfoItem());
        
        // 3. Decorative Border
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) gui.setItem(i, border); // Top row
        for (int i = 36; i < 45; i++) gui.setItem(i, border); // Bottom row
        gui.setItem(9, border); gui.setItem(17, border);
        gui.setItem(18, border); gui.setItem(26, border);
        gui.setItem(27, border); gui.setItem(35, border);

        // 4. Fillers (Dark Gray Glass)
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
        
        // 5. Override Top Center (Info) if it was overwritten
        gui.setItem(4, createInfoItem());

        player.openInventory(gui);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(GUI_TITLE)) return;
        e.setCancelled(true);
        
        if (e.getCurrentItem() == null) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        ItemStack item = e.getCurrentItem();
        
        // Check permissions
        if (!player.hasPermission("silentacore.gui") && !player.hasPermission("silentacore.admin")) {
            return;
        }
        
        // Toggle Logic
        // We identify modules by checking the lore for the key
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return;
        
        String key = null;
        for (String line : meta.getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (plugin.getOptimizationManager().getKeys().contains(stripped)) {
                key = stripped;
                break;
            }
        }
        
        if (key != null) {
            // Permission check for toggle
            if (!player.hasPermission("silentacore.toggle") && !player.hasPermission("silentacore.admin")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to toggle modules.");
                return;
            }
            
            boolean currentState = plugin.getOptimizationManager().isEnabled(key);
            plugin.getOptimizationManager().toggle(key, !currentState);
            
            // Play Sound
            // Use generic sounds to be safe across versions, or try/catch
            try {
                player.playSound(player.getLocation(), 
                    !currentState ? org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING : org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 
                    1f, !currentState ? 2f : 0.5f);
            } catch (Exception ignored) {}
            
            openGui(player); // Refresh
        }
    }

    private ItemStack createModuleItem(String name, String key, boolean enabled) {
        // Use different materials for better visuals
        Material mat = enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((enabled ? ChatColor.GREEN : ChatColor.RED) + "" + ChatColor.BOLD + name);
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "------------------------");
        lore.add(ChatColor.GRAY + "Status: " + (enabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to toggle");
        lore.add(ChatColor.DARK_GRAY + key); // Hidden key at bottom
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Server Status");
        
        List<String> lore = new ArrayList<>();
        double tps = plugin.getPerformanceMonitor().getCurrentTps();
        ChatColor tpsColor = tps > 19.5 ? ChatColor.GREEN : tps > 18.0 ? ChatColor.YELLOW : ChatColor.RED;
        
        lore.add(ChatColor.DARK_GRAY + "------------------------");
        lore.add(ChatColor.GRAY + "TPS: " + tpsColor + String.format("%.2f", tps));
        
        String level = plugin.getPerformanceMonitor().isLevel3() ? ChatColor.RED + "Level 3 (Emergency)" :
                       plugin.getPerformanceMonitor().isLevel2() ? ChatColor.GOLD + "Level 2 (Moderate)" :
                       plugin.getPerformanceMonitor().isLevel1() ? ChatColor.YELLOW + "Level 1 (Light)" : ChatColor.GREEN + "Vanilla";
                       
        lore.add(ChatColor.GRAY + "Load Level: " + level);
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
