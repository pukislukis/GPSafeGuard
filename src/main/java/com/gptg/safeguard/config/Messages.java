package com.gptg.safeguard.config;

import com.gptg.safeguard.GPSafeGuardPlugin;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Membaca messages.yml dan merender string MiniMessage, dengan penggantian
 * placeholder sederhana (String.replace) SEBELUM parsing MiniMessage supaya
 * placeholder aman dipakai di dalam argumen tag seperti <click:run_command:'...'>.
 */
public class Messages {

    private final GPSafeGuardPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration messages;
    private String prefix = "";

    public Messages(GPSafeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);

        // Merge default keys yang mungkin belum ada (misal setelah update plugin)
        try (InputStream defStream = plugin.getResource("messages.yml")) {
            if (defStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defStream, StandardCharsets.UTF_8));
                messages.setDefaults(defaults);
                messages.options().copyDefaults(true);
            }
        } catch (IOException ignored) {
        }

        prefix = messages.getString("prefix", "");
    }

    /** Cek apakah suatu message key dikosongkan ("") oleh admin untuk menonaktifkan notifikasi. */
    public boolean isBlank(String path) {
        String s = messages.getString(path);
        return s == null || s.isBlank();
    }

    /** Ambil raw string mentah (belum di-parse) dari messages.yml, dengan "<prefix>" sudah disubstitusi. */
    private String raw(String path) {
        String s = messages.getString(path);
        if (s == null) return "<red>Missing message: " + path + "</red>";
        return s.replace("<prefix>", prefix);
    }

    public Component render(String path) {
        return render(path, Map.of());
    }

    public Component render(String path, Map<String, String> placeholders) {
        String s = raw(path);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            s = s.replace("<" + e.getKey() + ">", e.getValue());
        }
        return miniMessage.deserialize(s);
    }

    public String renderPlain(String path, Map<String, String> placeholders) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(render(path, placeholders));
    }

    public void send(Audience audience, String path) {
        audience.sendMessage(render(path));
    }

    public void send(Audience audience, String path, Map<String, String> placeholders) {
        audience.sendMessage(render(path, placeholders));
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }
}
