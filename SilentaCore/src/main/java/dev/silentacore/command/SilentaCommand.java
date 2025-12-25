                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            package dev.silentacore.command;

import dev.silentacore.SilentaCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SilentaCommand implements CommandExecutor, TabCompleter {

    private final SilentaCore plugin;

    public SilentaCommand(SilentaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Base permission check
        if (!sender.hasPermission("silentacore.admin") && !sender.hasPermission("silentacore.use")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        
        // Granular permissions check
        if (!hasPermission(sender, sub)) {
             sender.sendMessage(ChatColor.RED + "No permission for this command.");
             return true;
        }

        switch (sub) {
            case "gui":
                if (sender instanceof Player) {
                    plugin.getGuiManager().openGui((Player) sender);
                } else {
                    sender.sendMessage(ChatColor.RED + "This command is for players only.");
                }
                break;
            case "status":
                sendStatus(sender);
                break;
            case "reload":
                plugin.getConfigManager().reload();
                plugin.getOptimizationManager().disableOptimizations();
                plugin.getOptimizationManager().enableOptimizations(); // Re-apply config state
                sender.sendMessage(ChatColor.GREEN + "SilentaCore configuration reloaded.");
                break;
            case "modules":
                sendModules(sender);
                break;
            case "toggle":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /sc toggle <module>");
                    return true;
                }
                toggleModule(sender, args[1]);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (hasPermission(sender, "status")) subs.add("status");
            if (hasPermission(sender, "gui")) subs.add("gui");
            if (hasPermission(sender, "reload")) subs.add("reload");
            if (hasPermission(sender, "modules")) subs.add("modules");
            if (hasPermission(sender, "toggle")) subs.add("toggle");
            
            StringUtil.copyPartialMatches(args[0], subs, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("toggle")) {
                if (hasPermission(sender, "toggle")) {
                    StringUtil.copyPartialMatches(args[1], plugin.getOptimizationManager().getKeys(), completions);
                }
            }
        }
        
        Collections.sort(completions);
        return completions;
    }

    private boolean hasPermission(CommandSender sender, String sub) {
        if (sender.hasPermission("silentacore.admin")) return true;
        
        return switch (sub) {
            case "gui" -> sender.hasPermission("silentacore.gui");
            case "status" -> sender.hasPermission("silentacore.status");
            case "reload" -> sender.hasPermission("silentacore.reload");
            case "modules" -> sender.hasPermission("silentacore.modules");
            case "toggle" -> sender.hasPermission("silentacore.toggle");
            default -> false;
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- SilentaCore v" + plugin.getDescription().getVersion() + " ---");
        if (hasPermission(sender, "gui")) sender.sendMessage(ChatColor.YELLOW + "/sc gui" + ChatColor.WHITE + " - Open control panel");
        if (hasPermission(sender, "status")) sender.sendMessage(ChatColor.YELLOW + "/sc status" + ChatColor.WHITE + " - View performance stats");
        if (hasPermission(sender, "modules")) sender.sendMessage(ChatColor.YELLOW + "/sc modules" + ChatColor.WHITE + " - List all modules");
        if (hasPermission(sender, "toggle")) sender.sendMessage(ChatColor.YELLOW + "/sc toggle <module>" + ChatColor.WHITE + " - Toggle a module");
        if (hasPermission(sender, "reload")) sender.sendMessage(ChatColor.YELLOW + "/sc reload" + ChatColor.WHITE + " - Reload configuration");
    }

    private void sendModules(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Optimization Modules ---");
        for (String key : plugin.getOptimizationManager().getKeys()) {
            boolean enabled = plugin.getOptimizationManager().isEnabled(key);
            String name = plugin.getOptimizationManager().getOptimizer(key).getName();
            sender.sendMessage((enabled ? ChatColor.GREEN : ChatColor.RED) + name + ChatColor.GRAY + " (" + key + ")");
        }
    }
    
    private void toggleModule(CommandSender sender, String moduleNameOrKey) {
        // Try to find key
        String targetKey = null;
        for (String key : plugin.getOptimizationManager().getKeys()) {
            if (key.equalsIgnoreCase(moduleNameOrKey)) {
                targetKey = key;
                break;
            }
            // Also try fuzzy match on name
            String name = plugin.getOptimizationManager().getOptimizer(key).getName();
            if (name.toLowerCase().contains(moduleNameOrKey.toLowerCase())) {
                targetKey = key;
                break;
            }
        }
        
        if (targetKey == null) {
            sender.sendMessage(ChatColor.RED + "Module not found: " + moduleNameOrKey);
            return;
        }
        
        boolean newState = !plugin.getOptimizationManager().isEnabled(targetKey);
        plugin.getOptimizationManager().toggle(targetKey, newState);
        sender.sendMessage(ChatColor.GREEN + "Toggled " + targetKey + " to " + (newState ? "ENABLED" : "DISABLED"));
    }

    private void sendStatus(CommandSender sender) {
        double tps = plugin.getPerformanceMonitor().getCurrentTps();
        ChatColor color = tps > 19.5 ? ChatColor.GREEN : tps > 18.0 ? ChatColor.YELLOW : ChatColor.RED;
        
        sender.sendMessage(ChatColor.GOLD + "--- SilentaCore Status ---");
        sender.sendMessage(ChatColor.GRAY + "TPS: " + color + String.format("%.2f", tps));
        
        boolean l1 = plugin.getPerformanceMonitor().isLevel1();
        boolean l2 = plugin.getPerformanceMonitor().isLevel2();
        boolean l3 = plugin.getPerformanceMonitor().isLevel3();
        
        String level = l3 ? "Level 3 (Emergency)" : l2 ? "Level 2 (Moderate)" : l1 ? "Level 1 (Light)" : "Idle (Vanilla)";
        sender.sendMessage(ChatColor.GRAY + "Optimization Level: " + ChatColor.AQUA + level);
    }
}
