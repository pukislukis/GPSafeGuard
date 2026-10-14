package com.gptg.safeguard.command;

import com.gptg.safeguard.GPSafeGuardPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "debug", "shelfguard", "dropfix", "info");

    private final GPSafeGuardPlugin plugin;

    public AdminCommand(GPSafeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gpsafeguard.admin") && !sender.hasPermission("gptrustguard.admin")) {
            plugin.getMessages().send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            sendInfo(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadAll();
                plugin.getMessages().send(sender, "general.reload-success");
            }
            case "debug" -> {
                boolean newState = !plugin.getConfigManager().isDebug();
                plugin.getConfigManager().setDebug(newState);
                plugin.getMessages().send(sender, newState ? "admin.debug-on" : "admin.debug-off");
            }
            case "shelfguard" -> {
                boolean newState = !plugin.getConfigManager().isShelfGuardEnabled();
                plugin.getConfigManager().setShelfGuardEnabled(newState);
                plugin.getMessages().send(sender, newState ? "shelf-guard.toggled-on" : "shelf-guard.toggled-off");
            }
            case "dropfix" -> {
                boolean newState = !plugin.getConfigManager().isDropFixEnabled();
                plugin.getConfigManager().setDropFixEnabled(newState);
                plugin.getMessages().send(sender, newState ? "drop-fix.toggled-on" : "drop-fix.toggled-off");
            }
            case "info" -> sendInfo(sender);
            default -> plugin.getMessages().send(sender, "admin.unknown-subcommand");
        }
        return true;
    }

    private void sendInfo(CommandSender sender) {
        Map<String, String> vars = Map.of(
                "version", plugin.getDescription().getVersion(),
                "trust_status", plugin.getConfigManager().isTrustEnabled() ? "ON" : "OFF",
                "shelf_status", plugin.getConfigManager().isShelfGuardEnabled() ? "ON" : "OFF",
                "blacklist_count", String.valueOf(plugin.getConfigManager().getBlacklistedBlocks().size()),
                "ability_status", plugin.getConfigManager().isAbilityProtectionEnabled() ? "ON" : "OFF",
                "ability_hooks", plugin.getAbilityHooks().describe(),
                "dropfix_status", plugin.getConfigManager().isDropFixEnabled() ? "ON" : "OFF"
        );
        plugin.getMessages().send(sender, "admin.info", vars);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
