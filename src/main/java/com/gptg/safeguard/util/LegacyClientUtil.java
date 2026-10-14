package com.gptg.safeguard.util;

import com.gptg.safeguard.GPSafeGuardPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Viewer capability detection; never use the shelf placer or the server version. */
public final class LegacyClientUtil {
    public record Detection(boolean needsLabel, String reason) {}
    private static final Map<UUID, String> lastDebug = new HashMap<>();
    private static final java.util.Set<String> warnings = new java.util.HashSet<>();
    private LegacyClientUtil() {}

    public static boolean needsShelfLabel(GPSafeGuardPlugin plugin, Player player) {
        return detect(plugin, player).needsLabel();
    }

    public static Detection detect(GPSafeGuardPlugin plugin, Player player) {
        Detection result = detectClient(plugin, player);
        if (plugin.getConfigManager().isShelfVisibleToAll()) result = new Detection(true, result.reason() + "; visible-to-all");
        String diagnostic = result.reason() + "; eligible=" + result.needsLabel();
        if (plugin.getConfigManager().isDebug() && !diagnostic.equals(lastDebug.put(player.getUniqueId(), diagnostic))) {
            plugin.getLogger().info("[debug] Shelf viewer: player=" + player.getName() + " uuid=" + player.getUniqueId() + " " + diagnostic);
        }
        return result;
    }

    private static Detection detectClient(GPSafeGuardPlugin plugin, Player player) {
        // Geyser also works with online authentication where Floodgate is absent.
        for (Plugin dependency : plugin.getServer().getPluginManager().getPlugins()) {
            if (!dependency.isEnabled()) continue;
            String name = dependency.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("geyser")) {
                try {
                    Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi", true, dependency.getClass().getClassLoader());
                    Object api = apiClass.getMethod("api").invoke(null);
                    Object connection = apiClass.getMethod("connectionByUuid", UUID.class).invoke(api, player.getUniqueId());
                    if (connection != null) {
                        // The public API exposes Bedrock protocol, not block mapping capability.
                        return new Detection(true, "Bedrock/Geyser (shelf mapping not advertised; conservative label)");
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                    warn(plugin, "Geyser", ex);
                }
            }
            if (name.equals("floodgate")) {
                try {
                    Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi", true, dependency.getClass().getClassLoader());
                    Object api = apiClass.getMethod("getInstance").invoke(null);
                    Object floodgatePlayer = apiClass.getMethod("getPlayer", UUID.class).invoke(api, player.getUniqueId());
                    if (floodgatePlayer == null) {
                        // Linked Bedrock accounts may be keyed by their original Floodgate UUID.
                        Object players = apiClass.getMethod("getPlayers").invoke(api);
                        if (players instanceof Iterable<?> iterable) {
                            for (Object candidate : iterable) {
                                Class<?> playerApi = Class.forName("org.geysermc.floodgate.api.player.FloodgatePlayer", true,
                                        dependency.getClass().getClassLoader());
                                if (player.getUniqueId().equals(playerApi.getMethod("getCorrectUniqueId").invoke(candidate))) {
                                    floodgatePlayer = candidate;
                                    break;
                                }
                            }
                        }
                    }
                    if (floodgatePlayer != null) {
                        Class<?> playerApi = Class.forName("org.geysermc.floodgate.api.player.FloodgatePlayer", true, dependency.getClass().getClassLoader());
                        String version = String.valueOf(playerApi.getMethod("getVersion").invoke(floodgatePlayer));
                        return new Detection(true, "Bedrock/Floodgate version=" + version + " (conservative shelf mapping label)");
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                    warn(plugin, "Floodgate", ex);
                }
            }
        }
        // Prefer ViaVersion's original client protocol over the translated backend protocol.
        Plugin via = plugin.getServer().getPluginManager().getPlugin("ViaVersion");
        if (via != null && via.isEnabled()) {
            try {
                ClassLoader loader = via.getClass().getClassLoader();
                Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via", true, loader);
                Object api = viaClass.getMethod("getAPI").invoke(null);
                Class<?> apiClass = Class.forName("com.viaversion.viaversion.api.ViaAPI", true, loader);
                int protocol = ((Number) apiClass.getMethod("getPlayerVersion", UUID.class).invoke(api, player.getUniqueId())).intValue();
                if (protocol > 0) return javaClient(protocol, "ViaVersion");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                warn(plugin, "ViaVersion", ex);
            }
        }
        // Crucial fallback: Paper retains the handshake protocol even without a local Via plugin.
        int protocol = player.getProtocolVersion();
        if (protocol > 0) return javaClient(protocol, "Paper handshake");
        return new Detection(false, "unknown client protocol; check backend ViaVersion/Floodgate/Geyser forwarding");
    }

    private static Detection javaClient(int protocol, String source) {
        return new Detection(protocol < 773, "Java protocol=" + protocol + " source=" + source + " shelfMin=773 (1.21.9)");
    }

    private static void warn(GPSafeGuardPlugin plugin, String hook, Throwable error) {
        if (warnings.add(hook)) plugin.getLogger().warning("Shelf client hook " + hook + " failed: " + error);
    }

    public static void forget(UUID player) { lastDebug.remove(player); }
    public static void clear() { lastDebug.clear(); warnings.clear(); }
}
