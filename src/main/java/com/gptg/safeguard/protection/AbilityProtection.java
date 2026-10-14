package com.gptg.safeguard.protection;

import com.gptg.safeguard.GPSafeGuardPlugin;
import com.gptg.safeguard.util.GriefPreventionBridge;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Shared, stateless claim policy. Only notification throttling is session data. */
public final class AbilityProtection {
    private final GPSafeGuardPlugin plugin;
    private final Map<UUID, Long> lastMessage = new HashMap<>();
    // Weak event keys correlate a MythicLib wrapper to its exact Bukkit event.
    // Never correlate by time/location: concurrent AoE attacks must stay separate.
    private final Map<EntityDamageEvent, UUID> damageAttackers = new WeakHashMap<>();
    private final Map<EntityDamageEvent, Boolean> skillEvents = new WeakHashMap<>();

    public AbilityProtection(GPSafeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isProtectedTarget(Entity target) {
        if (target == null) return false;
        if (target instanceof Player) return plugin.getConfigManager().isProtectPlayersFromAbilities();
        if (plugin.getConfigManager().isAllMobsProtected()) return true;
        String vanilla = target.getType().getKey().toString();
        if (plugin.getConfigManager().isMobWhitelisted(vanilla)
                || plugin.getConfigManager().isMobWhitelisted(target.getType().name())) return true;
        String mythic = plugin.getAbilityHooks().mythicMobId(target);
        return mythic != null && plugin.getConfigManager().isMobWhitelisted("mythicmobs:" + mythic);
    }

    public boolean shouldBlock(Player attacker, Entity target, Event event) {
        if (!plugin.getConfigManager().isAbilityProtectionEnabled() || !isProtectedTarget(target)) return false;
        var claim = GriefPreventionBridge.getClaimAt(target.getLocation());
        if (claim == null) return false;
        if (attacker == null) {
            if (event instanceof org.bukkit.event.entity.EntityPotionEffectEvent
                    || event instanceof io.papermc.paper.event.entity.EntityKnockbackEvent) {
                return plugin.getConfigManager().isBlockUnattributedPluginEffects();
            }
            return plugin.getConfigManager().isBlockUnattributedSkillDamage();
        }
        if ((attacker.hasPermission("gpsafeguard.bypass.abilityprotection") || attacker.hasPermission("gptrustguard.bypass.abilityprotection"))) return false;
        return !GriefPreventionBridge.canDamageEntities(attacker, claim, event);
    }

    public void blocked(Player player, String source, Entity target) {
        if (player != null && plugin.getConfigManager().isAbilityNotifyEnabled()) {
            long now = System.currentTimeMillis();
            Long previous = lastMessage.get(player.getUniqueId());
            if (previous == null || now - previous >= plugin.getConfigManager().getAbilityMessageCooldownMillis()) {
                lastMessage.put(player.getUniqueId(), now);
                plugin.getMessages().send(player, "ability-protection.blocked");
            }
        }
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[debug] Ability blocked via " + source + ": attacker="
                    + (player == null ? "unattributed" : player.getName()) + ", target="
                    + (target == null ? "cast area" : target.getType() + "/" + target.getUniqueId()));
        }
    }

    /** Optional conservative pre-cast check; ordinary attacks never call this. */
    public boolean shouldBlockCast(Player player, Event event) {
        if (player == null || !plugin.getConfigManager().isAbilityProtectionEnabled()
                || !plugin.getConfigManager().isAbilityCastGuardEnabled()
                || (player.hasPermission("gpsafeguard.bypass.abilityprotection") || player.hasPermission("gptrustguard.bypass.abilityprotection"))) return false;
        var claim = GriefPreventionBridge.getClaimAt(player.getLocation());
        if (claim != null && !GriefPreventionBridge.canDamageEntities(player, claim, event)) return true;
        int radius = plugin.getConfigManager().getAbilityCastRadius();
        for (Entity target : player.getNearbyEntities(radius, radius, radius)) {
            if (shouldBlock(player, target, event)) return true;
        }
        return false;
    }

    public void forget(UUID player) { lastMessage.remove(player); }
    public void rememberAttacker(Object event, Player attacker) {
        if (event instanceof EntityDamageEvent damage && attacker != null) {
            damageAttackers.put(damage, attacker.getUniqueId());
        }
    }
    public Player rememberedAttacker(EntityDamageEvent event) {
        UUID id = damageAttackers.get(event);
        return id == null ? null : plugin.getServer().getPlayer(id);
    }
    public void markSkill(Object event, boolean skill) {
        if (event instanceof EntityDamageEvent damage) skillEvents.put(damage, skill);
    }
    public Boolean skillClassification(EntityDamageEvent event) { return skillEvents.get(event); }
    public void clear() {
        lastMessage.clear();
        damageAttackers.clear();
        skillEvents.clear();
    }
}
