package com.gptg.safeguard.listener;

import com.gptg.safeguard.GPSafeGuardPlugin;
import com.gptg.safeguard.protection.AbilityProtection;
import com.gptg.safeguard.protection.AbilitySourceResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AbilityProtectionListener implements Listener {
    private final GPSafeGuardPlugin plugin;
    private final AbilityProtection protection;

    public AbilityProtectionListener(GPSafeGuardPlugin plugin, AbilityProtection protection) {
        this.plugin = plugin;
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!plugin.getConfigManager().isAbilityProtectionEnabled()) return;
        Player attacker = protection.rememberedAttacker(event);
        if (attacker == null) attacker = AbilitySourceResolver.player(event.getDamageSource().getCausingEntity());
        if (attacker == null) attacker = AbilitySourceResolver.player(event.getDamageSource().getDirectEntity());
        if (attacker == null && event instanceof EntityDamageByEntityEvent byEntity) {
            attacker = AbilitySourceResolver.player(byEntity.getDamager());
            if (attacker == null) attacker = plugin.getAbilityHooks().resolveMythicOwner(byEntity.getDamager());
        }
        // Normal melee, sweeping, bows and mob attacks are not governed by this plugin.
        Boolean skill = protection.skillClassification(event);
        if (Boolean.FALSE.equals(skill)) return;
        if (!Boolean.TRUE.equals(skill) && event.getCause() != EntityDamageEvent.DamageCause.CUSTOM
                && event.getCause() != EntityDamageEvent.DamageCause.MAGIC) return;
        if (protection.shouldBlock(attacker, event.getEntity(), event)) {
            event.setCancelled(true);
            protection.blocked(attacker, "Bukkit damage", event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        // Fire Aspect on a regular hit is left to GP.
        if (event.getCombuster() instanceof Player || event.getCombuster() instanceof org.bukkit.entity.Projectile) return;
        Player attacker = AbilitySourceResolver.player(event.getCombuster());
        if (attacker == null) attacker = plugin.getAbilityHooks().resolveMythicOwner(event.getCombuster());
        if (attacker != null && protection.shouldBlock(attacker, event.getEntity(), event)) {
            event.setCancelled(true);
            protection.blocked(attacker, "combust", event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        Player attacker = AbilitySourceResolver.player(event.getPotion());
        if (attacker == null) return;
        for (var target : event.getAffectedEntities()) {
            if (protection.shouldBlock(attacker, target, event)) {
                event.setIntensity(target, 0);
                protection.blocked(attacker, "potion splash", target);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        Player attacker = AbilitySourceResolver.player(event.getEntity());
        if (attacker == null) return;
        event.getAffectedEntities().removeIf(target -> {
            if (!protection.shouldBlock(attacker, target, event)) return false;
            protection.blocked(attacker, "area effect cloud", target);
            return true;
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPluginEffect(EntityPotionEffectEvent event) {
        // No caster is exposed by Bukkit for addPotionEffect(). Never remove an
        // existing effect or block expiration/milk/death cleanup.
        if (event.getCause() != EntityPotionEffectEvent.Cause.PLUGIN || event.getNewEffect() == null
                || !plugin.getConfigManager().isBlockUnattributedPluginEffects()) return;
        if (protection.shouldBlock(null, event.getEntity(), event)) {
            event.setCancelled(true);
            protection.blocked(null, "unattributed plugin potion effect", event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPluginKnockback(io.papermc.paper.event.entity.EntityKnockbackEvent event) {
        if (!"UNKNOWN".equals(event.getCause().name()) || !plugin.getConfigManager().isBlockUnattributedPluginEffects()) return;
        if (protection.shouldBlock(null, event.getEntity(), event)) {
            event.setCancelled(true);
            protection.blocked(null, "unattributed plugin knockback", event.getEntity());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        protection.forget(event.getPlayer().getUniqueId());
    }
}
