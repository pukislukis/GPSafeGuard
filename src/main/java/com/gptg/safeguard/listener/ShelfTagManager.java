package com.gptg.safeguard.listener;

import com.gptg.safeguard.GPSafeGuardPlugin;
import com.gptg.safeguard.util.LegacyClientUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.Locale;

/**
 * Mengelola entity TextDisplay "label" yang ditempel di atas block SHELF,
 * dengan tracking filter per viewer: Java legacy dan Bedrock yang terdeteksi.
 *
 * Kenapa TextDisplay, bukan custom name block biasa?
 * Karena block SHELF bukan block ber-GUI (interaksinya instant-swap seperti
 * lectern), jadi tidak ada "judul GUI" yang bisa dipakai untuk menandai block
 * tsb. TextDisplay entity native di Paper API bisa disembunyikan/ditampilkan
 * per-player TANPA perlu ProtocolLib.
 */
public class ShelfTagManager implements Listener {

    private final GPSafeGuardPlugin plugin;
    private BukkitTask revealTask;
    private final java.util.Map<java.util.UUID, Long> visibilityDebug = new java.util.HashMap<>();

    public ShelfTagManager(GPSafeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        // Existing persistent tags are refreshed by the periodic viewer scan.
        long interval = Math.max(20L, plugin.getConfigManager().getTagRevealIntervalTicks());
        revealTask = Bukkit.getScheduler().runTaskTimer(plugin, this::revealToAllBedrockPlayers, 40L, interval);
    }

    public void stop() {
        if (revealTask != null) {
            revealTask.cancel();
            revealTask = null;
        }
    }

    /** Dipanggil setelah sebuah shelf block berhasil di-place. */
    public void tagBlock(Block block, Material material) {
        if (!plugin.getConfigManager().isTagForLegacyClients()) {
            debug("Shelf tag skipped: tag-for-legacy-clients=false location=" + block.getLocation());
            return;
        }
        removeTagAt(block);

        String prettyName = toPrettyName(material);
        String formatted = plugin.getConfigManager().getTagFormat()
                .replace("%version%", plugin.getConfigManager().getTagVersionLabel())
                .replace("%block%", prettyName);
        Component text = plugin.getMessages().getMiniMessage().deserialize(formatted);

        Location loc = block.getLocation().add(0.5, 1.05, 0.5);

        TextDisplay display;
        try {
            display = block.getWorld().spawn(loc, TextDisplay.class, td -> {
                td.text(text);
                td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                td.setSeeThrough(true);
                td.setShadowed(false);
                td.setDefaultBackground(false);
                td.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                td.setPersistent(true);
                td.getPersistentDataContainer().set(plugin.getShelfTagKey(), PersistentDataType.STRING, material.name());
                // Let eligible viewers enter normal entity tracking. The tracking
                // event below rejects modern viewers BEFORE a spawn packet is sent.
                td.setVisibleByDefault(true);
            });
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Shelf TextDisplay spawn failed at " + loc, ex);
            return;
        }
        debug("Shelf TextDisplay spawned: uuid=" + display.getUniqueId() + " valid=" + display.isValid()
                + " location=" + loc + " text=" + formatted);

        // Langsung tampilkan ke player Bedrock yang sedang online & dekat lokasi ini.
        int viewers = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (LegacyClientUtil.needsShelfLabel(plugin, online) && online.getWorld().equals(display.getWorld())) {
                applyVisibility(online, display, true);
                viewers++;
            } else if (online.getWorld().equals(display.getWorld())) {
                applyVisibility(online, display, false);
            }
        }
        debug("Shelf TextDisplay visibility: uuid=" + display.getUniqueId() + " eligibleViewers=" + viewers
                + " (Java legacy needs ViaVersion; Bedrock needs Floodgate; tag-visible-to-all enables all)");
    }

    /** Hapus tag di lokasi block (dipanggil saat block SHELF dihancurkan). */
    public void removeTagAt(Block block) {
        Location center = block.getLocation().add(0.5, 1.05, 0.5);
        for (Entity e : block.getWorld().getNearbyEntities(center, 0.6, 0.6, 0.6)) {
            if (e instanceof TextDisplay td && isShelfTag(td)) {
                td.remove();
            }
        }
    }

    /** Tampilkan ulang semua tag terdekat ke satu player Bedrock (dipanggil saat join). */
    public void revealTo(Player player) {
        boolean visible = plugin.getConfigManager().isTagForLegacyClients()
                && LegacyClientUtil.needsShelfLabel(plugin, player);
        int radius = plugin.getConfigManager().getTagRevealRadius();
        Collection<Entity> nearby = player.getNearbyEntities(radius, radius, radius);
        for (Entity e : nearby) {
            if (e instanceof TextDisplay td && isShelfTag(td)) {
                String material = td.getPersistentDataContainer().get(plugin.getShelfTagKey(), PersistentDataType.STRING);
                Material type = Material.matchMaterial(material);
                if (type == null) continue;
                String formatted = plugin.getConfigManager().getTagFormat()
                        .replace("%version%", plugin.getConfigManager().getTagVersionLabel())
                        .replace("%block%", toPrettyName(type));
                td.text(plugin.getMessages().getMiniMessage().deserialize(formatted));
                // Migrate tags from previous builds' hidden-by-default state.
                if (!td.isVisibleByDefault()) {
                    for (Player online : Bukkit.getOnlinePlayers()) online.hideEntity(plugin, td);
                    td.setVisibleByDefault(true);
                }
                applyVisibility(player, td, visible);
            }
        }
        if (plugin.getConfigManager().isDebug()) {
            long now = System.currentTimeMillis();
            Long previous = visibilityDebug.get(player.getUniqueId());
            if (previous == null || now - previous >= 15000L) {
                visibilityDebug.put(player.getUniqueId(), now);
                long labels = nearby.stream().filter(e -> e instanceof TextDisplay td && isShelfTag(td)).count();
                debug("Shelf visibility scan: viewer=" + player.getName() + " eligible=" + visible
                        + " nearbyLabels=" + labels + " radius=" + radius);
            }
        }
    }

    private void revealToAllBedrockPlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            revealTo(p);
        }
    }

    private boolean isShelfTag(TextDisplay td) {
        return td.getPersistentDataContainer().has(plugin.getShelfTagKey(), PersistentDataType.STRING);
    }

    private void applyVisibility(Player viewer, TextDisplay display, boolean eligible) {
        boolean before = viewer.canSee(display);
        if (eligible) viewer.showEntity(plugin, display);
        else viewer.hideEntity(plugin, display);
        // canSee means server visibility, not proof of client rendering.
        if (before != viewer.canSee(display)) {
            debug("Shelf visibility applied: viewer=" + viewer.getName() + " label=" + display.getUniqueId()
                    + " eligible=" + eligible + " serverCanSee=" + viewer.canSee(display));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrack(PlayerTrackEntityEvent event) {
        if (!(event.getEntity() instanceof TextDisplay td) || !isShelfTag(td)) return;
        boolean eligible = plugin.getConfigManager().isTagForLegacyClients()
                && LegacyClientUtil.needsShelfLabel(plugin, event.getPlayer());
        if (!eligible) event.setCancelled(true);
        debug("Shelf tracking: viewer=" + event.getPlayer().getName() + " label=" + td.getUniqueId()
                + " eligible=" + eligible + " cancelled=" + event.isCancelled());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) { refreshSoon(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { refreshSoon(event.getPlayer()); }

    private void refreshSoon(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) revealTo(player);
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        visibilityDebug.remove(event.getPlayer().getUniqueId());
        LegacyClientUtil.forget(event.getPlayer().getUniqueId());
    }

    private void debug(String message) {
        if (plugin.getConfigManager().isDebug()) plugin.getLogger().info("[debug] " + message);
    }

    private String toPrettyName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
