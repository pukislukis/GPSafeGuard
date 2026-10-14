package com.gptg.safeguard.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deteksi apakah player bermain lewat Bedrock (via Floodgate).
 * Floodgate bersifat soft-dependency: kalau plugin Floodgate tidak terpasang,
 * semua method di sini aman dan selalu return false.
 */
public final class BedrockUtil {

    private static Boolean floodgateAvailable;
    private static Logger logger;

    private BedrockUtil() {}

    public static void init(Logger pluginLogger) {
        logger = pluginLogger;
        floodgateAvailable = Bukkit.getPluginManager().getPlugin("floodgate") != null;
        if (logger != null) {
            logger.info(floodgateAvailable
                    ? "Floodgate terdeteksi: deteksi player Bedrock diaktifkan."
                    : "Floodgate tidak ditemukan: fitur khusus Bedrock (arahan run-command & tag legacy) dinonaktifkan.");
        }
    }

    public static boolean isFloodgateAvailable() {
        return Boolean.TRUE.equals(floodgateAvailable);
    }

    public static boolean isBedrock(Player player) {
        if (!isFloodgateAvailable() || player == null) return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable t) {
            if (logger != null) logger.log(Level.WARNING, "Gagal memeriksa status Bedrock player " + player.getName(), t);
            return false;
        }
    }

    public static boolean isBedrock(UUID uuid) {
        if (!isFloodgateAvailable() || uuid == null) return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        } catch (Throwable t) {
            return false;
        }
    }
}
