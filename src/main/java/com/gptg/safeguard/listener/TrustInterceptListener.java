package com.gptg.safeguard.listener;

import com.gptg.safeguard.GPSafeGuardPlugin;
import com.gptg.safeguard.trust.TrustType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Map;

/**
 * Meng-intercept command /trust /t /accesstrust /at /containertrust /ct
 * /permissiontrust /pt milik GriefPrevention SEBELUM dieksekusi oleh
 * GriefPrevention, lalu menggantinya dengan alur invite/accept/decline milik
 * plugin ini.
 *
 * Catatan: plugin ini SENGAJA tidak mendaftarkan command "trust" dkk di
 * plugin.yml supaya tidak berebut ownership command dengan GriefPrevention.
 * Sebagai gantinya kita cancel event ini lebih dulu (priority LOWEST).
 */
public class TrustInterceptListener implements Listener {

    private final GPSafeGuardPlugin plugin;

    public TrustInterceptListener(GPSafeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfigManager().isTrustEnabled()) return;

        String message = event.getMessage();
        if (message.isEmpty() || message.charAt(0) != '/') return;

        String[] parts = message.substring(1).split("\\s+");
        String label = parts[0].toLowerCase();

        TrustType type = TrustType.fromLabel(label);
        if (type == null) return; // bukan command yang kita intercept

        // Command ini milik kita sekarang: selalu cancel supaya GriefPrevention
        // tidak ikut memproses / memunculkan pesan "unknown command".
        event.setCancelled(true);

        Player sender = event.getPlayer();
        if ((!sender.hasPermission("gpsafeguard.trust.use") && !sender.hasPermission("gptrustguard.trust.use"))) {
            plugin.getMessages().send(sender, "general.no-permission");
            return;
        }

        // Khusus command dasar "/trust": dukung "/trust accept [player]" & "/trust decline [player]"
        if (type == TrustType.BUILD && parts.length >= 2) {
            String arg1 = parts[1];
            if (arg1.equalsIgnoreCase("accept")) {
                String target = parts.length >= 3 ? parts[2] : null;
                plugin.getTrustManager().accept(sender, target);
                return;
            }
            if (arg1.equalsIgnoreCase("decline") || arg1.equalsIgnoreCase("deny")) {
                String target = parts.length >= 3 ? parts[2] : null;
                plugin.getTrustManager().decline(sender, target);
                return;
            }
        }

        if (parts.length < 2) {
            plugin.getMessages().send(sender, "trust.usage", Map.of("label", label));
            return;
        }

        String targetName = parts[1];
        Player targetPlayer = Bukkit.getPlayerExact(targetName);
        if (targetPlayer == null) {
            plugin.getMessages().send(sender, "general.player-not-found", Map.of("target", targetName));
            return;
        }

        plugin.getTrustManager().startInvite(sender, targetPlayer, type);
    }
}
