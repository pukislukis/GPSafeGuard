package com.gptg.safeguard.listener;

import com.gptg.safeguard.GPSafeGuardPlugin;
import com.gptg.safeguard.util.GriefPreventionBridge;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Map;

public class ShelfGuardListener implements Listener {

    private final GPSafeGuardPlugin plugin;

    public ShelfGuardListener(GPSafeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfigManager().isShelfGuardEnabled()) return;

        Block block = event.getBlockPlaced();
        Material material = block.getType();
        if (!plugin.getConfigManager().isBlacklisted(material)) return;

        Player player = event.getPlayer();

        if (plugin.getConfigManager().isDenyOutsideLand() && (!player.hasPermission("gpsafeguard.bypass.shelfblacklist") && !player.hasPermission("gptrustguard.bypass.shelfblacklist"))) {
            Claim claim = GriefPreventionBridge.getClaimAt(block.getLocation());
            if (claim == null) {
                event.setCancelled(true);
                plugin.getMessages().send(player, "shelf-guard.blocked-place",
                        Map.of("block", prettyName(material)));
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().info("[debug] Blokir placement " + material + " oleh " + player.getName()
                            + " di luar land (" + block.getLocation() + ")");
                }
                return;
            }
        }

    }

    // Observe even cancelled shelf placements so debug explains why no tag exists.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlacementFinished(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Material material = block.getType();
        boolean shelf = material.name().endsWith("_SHELF");
        boolean listed = plugin.getConfigManager().isBlacklisted(material);
        if (!shelf && !listed) return;
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[debug] Shelf place: player=" + event.getPlayer().getName()
                    + " material=" + material + " item=" + event.getItemInHand().getType()
                    + " location=" + block.getLocation() + " listed=" + listed
                    + " cancelled=" + event.isCancelled() + " guard=" + plugin.getConfigManager().isShelfGuardEnabled());
        }
        if (event.isCancelled() || !plugin.getConfigManager().isShelfGuardEnabled() || (!shelf && !listed)) return;
        // Spawn only after the placement has committed, not during a cancellable event.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.getType() == material) plugin.getShelfTagManager().tagBlock(block, material);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Material material = event.getBlock().getType();
        if (!plugin.getConfigManager().isBlacklisted(material)) return;
        plugin.getShelfTagManager().removeTagAt(event.getBlock());
    }

    private String prettyName(Material material) {
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
