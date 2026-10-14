package com.gptg.safeguard.config;

import com.gptg.safeguard.GPSafeGuardPlugin;
import com.gptg.safeguard.trust.TrustType;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Memuat & menyediakan akses ke config.yml.
 * Semua id block di-resolve lewat Material.matchMaterial(String) supaya tetap
 * kompatibel walau nama constant Material belum tersedia di semua versi API.
 */
public class ConfigManager {

    private final GPSafeGuardPlugin plugin;
    private FileConfiguration cfg;

    private boolean debug;
    private boolean dropFixEnabled;

    private boolean trustEnabled;
    private int trustTimeoutSeconds;
    private boolean skipIfAlreadyTrusted;

    private boolean shelfGuardEnabled;
    private boolean denyOutsideLand;
    private boolean tagForLegacyClients;
    private String tagFormat;
    private String tagVersionLabel;
    private int tagRevealRadius;
    private long tagRevealIntervalTicks;
    private final Set<Material> blacklistedBlocks = new HashSet<>();
    private final Set<String> unresolvedBlacklistEntries = new HashSet<>();

    public ConfigManager(GPSafeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
        // Migrate defaults without overwriting explicit administrator settings.
        boolean migrated = false;
        if ("1.21.11".equals(cfg.getString("shelf-guard.tag-version-label"))) {
            cfg.set("shelf-guard.tag-version-label", "1.21.9");
            migrated = true;
        }
        if (!cfg.contains("drop-fix.enabled", true)) {
            cfg.set("drop-fix.enabled", true);
            migrated = true;
        }
        if (!cfg.contains("ability-protection.cast-guard", true)) {
            cfg.set("ability-protection.cast-guard.enabled", false);
            cfg.set("ability-protection.cast-guard.radius", 48);
            migrated = true;
        }
        if (!cfg.contains("ability-protection.mob-mode", true)) {
            cfg.set("ability-protection.mob-mode", "ALL");
            migrated = true;
        }
        // Upgrade only the previous stock whitelist; preserve custom lists/empty lists.
        List<String> oldWhitelist = cfg.getStringList("ability-protection.mob-whitelist");
        if (!cfg.contains("ability-protection.mob-whitelist", true)
                || oldWhitelist.size() == 2 && new HashSet<>(oldWhitelist).equals(
                        Set.of("minecraft:villager", "minecraft:iron_golem"))) {
            cfg.set("ability-protection.mob-whitelist",
                    cfg.getDefaults().getStringList("ability-protection.mob-whitelist"));
            migrated = true;
        }
        if (migrated) plugin.saveConfig();

        debug = cfg.getBoolean("debug", false);
        dropFixEnabled = cfg.getBoolean("drop-fix.enabled", true);

        trustEnabled = cfg.getBoolean("trust.enabled", true);
        trustTimeoutSeconds = Math.max(5, cfg.getInt("trust.invite-timeout-seconds", 30));
        skipIfAlreadyTrusted = cfg.getBoolean("trust.skip-if-already-trusted", true);

        shelfGuardEnabled = cfg.getBoolean("shelf-guard.enabled", true);
        denyOutsideLand = cfg.getBoolean("shelf-guard.deny-placement-outside-land", true);
        tagForLegacyClients = cfg.getBoolean("shelf-guard.tag-for-legacy-clients", true);
        tagFormat = cfg.getString("shelf-guard.tag-format", "<gray><italic>%version% %block%</italic></gray>");
        tagVersionLabel = cfg.getString("shelf-guard.tag-version-label", "1.21.9");
        tagRevealRadius = Math.max(1, cfg.getInt("shelf-guard.tag-reveal-radius", 48));
        tagRevealIntervalTicks = cfg.getLong("shelf-guard.tag-reveal-interval-ticks", 100L);

        loadBlacklist();
        loadTrustLabels();

        if (debug) {
            plugin.getLogger().info("[debug] Config dimuat. trustTimeout=" + trustTimeoutSeconds
                    + "s shelfGuard=" + shelfGuardEnabled + " blacklist=" + blacklistedBlocks.size()
                    + " dropFix=" + dropFixEnabled);
        }
    }

    private void loadBlacklist() {
        blacklistedBlocks.clear();
        unresolvedBlacklistEntries.clear();
        List<String> list = cfg.getStringList("shelf-guard.blacklisted-blocks");
        for (String raw : list) {
            if (raw == null || raw.isBlank()) continue;
            String id = raw.trim().toLowerCase().replace("minecraft:", "");
            Material mat = Material.matchMaterial(id);
            if (mat == null) {
                unresolvedBlacklistEntries.add(id);
                continue;
            }
            blacklistedBlocks.add(mat);
        }
        if (!unresolvedBlacklistEntries.isEmpty()) {
            plugin.getLogger().log(Level.WARNING,
                    "Beberapa block di shelf-guard.blacklisted-blocks tidak dikenali oleh server ini "
                            + "(mungkin versi server < 1.21.9, atau typo): " + unresolvedBlacklistEntries);
        }
    }

    private void loadTrustLabels() {
        TrustType.resetRegistry();
        registerLabels("trust.intercepted-commands.trust", TrustType.BUILD);
        registerLabels("trust.intercepted-commands.accesstrust", TrustType.ACCESS);
        registerLabels("trust.intercepted-commands.containertrust", TrustType.CONTAINER);
        registerLabels("trust.intercepted-commands.permissiontrust", TrustType.PERMISSION);
    }

    private void registerLabels(String path, TrustType type) {
        List<String> labels = cfg.getStringList(path);
        if (labels.isEmpty()) return;
        for (String l : labels) {
            TrustType.registerLabel(l, type);
        }
    }

    public File getConfigFile() {
        return new File(plugin.getDataFolder(), "config.yml");
    }

    // ---- getters & setters ----
    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) {
        this.debug = debug;
        cfg.set("debug", debug);
        plugin.saveConfig();
    }

    public boolean isDropFixEnabled() { return dropFixEnabled; }
    public void setDropFixEnabled(boolean enabled) {
        this.dropFixEnabled = enabled;
        cfg.set("drop-fix.enabled", enabled);
        plugin.saveConfig();
    }

    public boolean isTrustEnabled() { return trustEnabled; }
    public int getTrustTimeoutSeconds() { return trustTimeoutSeconds; }
    public boolean isSkipIfAlreadyTrusted() { return skipIfAlreadyTrusted; }

    public boolean isShelfGuardEnabled() { return shelfGuardEnabled; }
    public void setShelfGuardEnabled(boolean enabled) {
        this.shelfGuardEnabled = enabled;
        cfg.set("shelf-guard.enabled", enabled);
        plugin.saveConfig();
    }
    public boolean isDenyOutsideLand() { return denyOutsideLand; }
    public boolean isTagForLegacyClients() { return tagForLegacyClients; }
    public String getTagFormat() { return tagFormat; }
    public String getTagVersionLabel() { return tagVersionLabel; }
    public int getTagRevealRadius() { return tagRevealRadius; }
    public long getTagRevealIntervalTicks() { return tagRevealIntervalTicks; }

    public boolean isBlacklisted(Material material) {
        return blacklistedBlocks.contains(material);
    }

    public Set<Material> getBlacklistedBlocks() {
        return blacklistedBlocks;
    }

    public boolean isAbilityProtectionEnabled() { return cfg.getBoolean("ability-protection.enabled", true); }
    public boolean isAbilityCastGuardEnabled() { return cfg.getBoolean("ability-protection.cast-guard.enabled", false); }
    public int getAbilityCastRadius() { return Math.max(1, Math.min(128, cfg.getInt("ability-protection.cast-guard.radius", 48))); }
    public boolean isAbilityHookEnabled(String name) { return cfg.getBoolean("ability-protection.hooks." + name, true); }
    public boolean isProtectPlayersFromAbilities() { return cfg.getBoolean("ability-protection.protect-players", false); }
    public boolean isAllMobsProtected() {
        String mode = cfg.getString("ability-protection.mob-mode", "ALL");
        return !"WHITELIST".equalsIgnoreCase(mode);
    }
    public boolean isMobWhitelisted(String id) {
        return cfg.getStringList("ability-protection.mob-whitelist").stream()
                .map(entry -> entry.trim().toLowerCase(Locale.ROOT)).anyMatch(id.toLowerCase(Locale.ROOT)::equals);
    }
    public boolean isShelfVisibleToAll() { return cfg.getBoolean("shelf-guard.tag-visible-to-all", false); }
    public boolean isBlockUnattributedPluginEffects() { return cfg.getBoolean("ability-protection.block-unattributed-plugin-effects", true); }
    public boolean isBlockUnattributedSkillDamage() { return cfg.getBoolean("ability-protection.block-unattributed-skill-damage", true); }
    public boolean isAbilityNotifyEnabled() { return cfg.getBoolean("ability-protection.notify-player", true); }
    public long getAbilityMessageCooldownMillis() { return Math.max(1, cfg.getInt("ability-protection.message-cooldown-seconds", 3)) * 1000L; }
}
