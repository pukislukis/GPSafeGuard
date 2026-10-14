package com.gptg.safeguard;

import com.gptg.safeguard.command.AdminCommand;
import com.gptg.safeguard.config.ConfigManager;
import com.gptg.safeguard.config.Messages;
import com.gptg.safeguard.listener.AbilityProtectionListener;
import com.gptg.safeguard.listener.DropFixListener;
import com.gptg.safeguard.listener.PlayerJoinListener;
import com.gptg.safeguard.listener.ShelfGuardListener;
import com.gptg.safeguard.listener.ShelfTagManager;
import com.gptg.safeguard.listener.TrustInterceptListener;
import com.gptg.safeguard.protection.AbilityHookManager;
import com.gptg.safeguard.protection.AbilityProtection;
import com.gptg.safeguard.trust.TrustManager;
import com.gptg.safeguard.util.BedrockUtil;
import com.gptg.safeguard.util.LegacyClientUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class GPSafeGuardPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private Messages messages;
    private TrustManager trustManager;
    private ShelfTagManager shelfTagManager;
    private NamespacedKey shelfTagKey;
    private AbilityProtection abilityProtection;
    private AbilityHookManager abilityHooks;
    private DropFixListener dropFixListener;

    @Override
    public void onEnable() {
        this.shelfTagKey = new NamespacedKey(this, "shelf_tag");

        this.configManager = new ConfigManager(this);
        this.messages = new Messages(this);
        this.trustManager = new TrustManager(this);
        this.shelfTagManager = new ShelfTagManager(this);
        this.abilityProtection = new AbilityProtection(this);
        this.abilityHooks = new AbilityHookManager(this, abilityProtection);
        this.dropFixListener = new DropFixListener(this);

        BedrockUtil.init(getLogger());

        reloadAll();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new TrustInterceptListener(this), this);
        pm.registerEvents(new ShelfGuardListener(this), this);
        pm.registerEvents(new PlayerJoinListener(this), this);
        pm.registerEvents(shelfTagManager, this);
        pm.registerEvents(new AbilityProtectionListener(this, abilityProtection), this);
        pm.registerEvents(dropFixListener, this);
        abilityHooks.start();

        var adminCommand = new AdminCommand(this);
        var cmd = getCommand("gpsafeguard");
        if (cmd != null) {
            cmd.setExecutor(adminCommand);
            cmd.setTabCompleter(adminCommand);
        }

        shelfTagManager.start();

        getLogger().info("GPSafeGuard aktif. Trust intercept: " + configManager.isTrustEnabled()
                + " | Shelf guard: " + configManager.isShelfGuardEnabled()
                + " | Ability guard: " + configManager.isAbilityProtectionEnabled()
                + " | DropFix: " + configManager.isDropFixEnabled());
    }

    @Override
    public void onDisable() {
        if (abilityHooks != null) abilityHooks.stop();
        if (abilityProtection != null) abilityProtection.clear();
        if (shelfTagManager != null) shelfTagManager.stop();
        if (dropFixListener != null) dropFixListener.clear();
        LegacyClientUtil.clear();
        if (trustManager != null) trustManager.clearAll();
    }

    /** Reload config.yml & messages.yml, lalu re-apply state yang bergantung padanya. */
    public void reloadAll() {
        configManager.load();
        messages.load();
        if (shelfTagManager != null) {
            shelfTagManager.stop();
            shelfTagManager.start();
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public Messages getMessages() { return messages; }
    public TrustManager getTrustManager() { return trustManager; }
    public ShelfTagManager getShelfTagManager() { return shelfTagManager; }
    public NamespacedKey getShelfTagKey() { return shelfTagKey; }
    public AbilityHookManager getAbilityHooks() { return abilityHooks; }
    public DropFixListener getDropFixListener() { return dropFixListener; }
}
