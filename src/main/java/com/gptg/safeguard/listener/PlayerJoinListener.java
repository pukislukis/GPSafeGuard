package com.gptg.safeguard.listener;

import com.gptg.safeguard.GPSafeGuardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final GPSafeGuardPlugin plugin;

    public PlayerJoinListener(GPSafeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Delay singkat supaya entity di chunk sekitar sudah selesai load.
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getShelfTagManager().revealTo(event.getPlayer()), 20L);
    }
}
