package com.chatcleaner.commands;

import com.chatcleaner.ChatCleaner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ChatCleanerCommand implements CommandExecutor, TabCompleter {

    private final ChatCleaner plugin;

    public ChatCleanerCommand(ChatCleaner plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chatcleaner.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§7[§bChatCleaner§7] §fUsage: /chatcleaner <reload|status|whitelist>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.getConfigManager().loadConfig();
            sender.sendMessage("§7[§bChatCleaner§7] §aConfiguration reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§7[§bChatCleaner§7] §eStatus:");
            sender.sendMessage("§7- Banned Words: §f" + plugin.getConfigManager().getBannedWords().size());
            sender.sendMessage("§7- Whitelisted: §f" + plugin.getConfigManager().getWhitelist().size());
            return true;
        }

        if (args[0].equalsIgnoreCase("whitelist")) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /chatcleaner whitelist <add|remove> <word>");
                return true;
            }
            String word = args[2];
            if (args[1].equalsIgnoreCase("add")) {
                plugin.getConfigManager().addWhitelist(word);
                sender.sendMessage("§aAdded '" + word + "' to whitelist.");
            } else if (args[1].equalsIgnoreCase("remove")) {
                plugin.getConfigManager().removeWhitelist(word);
                sender.sendMessage("§aRemoved '" + word + "' from whitelist.");
            }
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("chatcleaner.admin")) return new ArrayList<>();

        if (args.length == 1) {
            return Arrays.asList("reload", "status", "whitelist").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("whitelist")) {
            return Arrays.asList("add", "remove").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("whitelist") && args[1].equalsIgnoreCase("remove")) {
            return plugin.getConfigManager().getWhitelist().stream()
                .filter(s -> s.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
