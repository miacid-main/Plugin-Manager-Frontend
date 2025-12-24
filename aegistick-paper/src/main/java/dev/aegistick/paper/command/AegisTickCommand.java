package dev.aegistick.paper.command;

import dev.aegistick.core.AegisTickCore;
import dev.aegistick.paper.AegisTickPlugin;
import dev.aegistick.paper.gui.AdminGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Command handler for /aegistick admin commands.
 */
public class AegisTickCommand implements CommandExecutor, TabCompleter {

    private final AegisTickCore core;
    private final AegisTickPlugin plugin;

    public AegisTickCommand(AegisTickCore core, AegisTickPlugin plugin) {
        this.core = core;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aegistick.admin")) {
            // Silent - no message to non-admins
            return true;
        }

        if (args.length == 0) {
            // Open GUI for players, show status for console
            if (sender instanceof Player player) {
                new AdminGui(core, plugin).open(player);
            } else {
                showStatus(sender);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status" -> showStatus(sender);
            case "toggle" -> toggleOptimization(sender);
            case "reload" -> reloadConfig(sender);
            case "emergency" -> handleEmergency(sender, args);
            default -> sender.sendMessage("Unknown subcommand. Use: status, toggle, reload, emergency");
        }

        return true;
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage("§6=== AegisTick Status ===");
        sender.sendMessage("§7Enabled: " + (core.isEnabled() ? "§aYes" : "§cNo"));
        sender.sendMessage("§7Emergency Mode: " + (core.isEmergencyMode() ? "§cACTIVE" : "§aInactive"));
        sender.sendMessage("§7Profiled Chunks: §f" + core.getChunkProfiler().getProfiledChunkCount());
        
        var stats = core.getOptimizationEngine().getStats();
        sender.sendMessage("§7Active Optimizations: §f" + stats.totalActiveOptimizations());
        sender.sendMessage("§7Chunks Optimized: §f" + stats.chunksOptimized());
        
        var learningStats = core.getLearningEngine().getStats();
        sender.sendMessage("§7Recent TPS: §f" + String.format("%.2f", learningStats.recentAverageTps()));
        sender.sendMessage("§7Lag Spike Predicted: " + (learningStats.lagSpikePredicted() ? "§cYes" : "§aNo"));
    }

    private void toggleOptimization(CommandSender sender) {
        boolean newState = !core.isEnabled();
        core.setEnabled(newState);
        sender.sendMessage("§6AegisTick " + (newState ? "§aenabled" : "§cdisabled"));
    }

    private void reloadConfig(CommandSender sender) {
        plugin.reloadConfiguration();
        sender.sendMessage("§6AegisTick configuration reloaded");
    }

    private void handleEmergency(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /aegistick emergency <force-exit|status>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "force-exit" -> {
                // Force exit emergency mode
                sender.sendMessage("§6Emergency mode force-exit requested");
            }
            case "status" -> {
                sender.sendMessage("§7Emergency Mode: " + (core.isEmergencyMode() ? "§cACTIVE" : "§aInactive"));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aegistick.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Arrays.asList("status", "toggle", "reload", "emergency");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("emergency")) {
            return Arrays.asList("force-exit", "status");
        }

        return Collections.emptyList();
    }
}
