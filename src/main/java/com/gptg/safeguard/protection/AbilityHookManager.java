package com.gptg.safeguard.protection;

import com.gptg.safeguard.GPSafeGuardPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Optional APIs are resolved from their owning plugin classloader. No optional
 * vendor classes leak into the core, and unsupported event shapes are reported
 * at startup instead of silently claiming a working hook.
 */
public final class AbilityHookManager implements Listener {
    private final GPSafeGuardPlugin plugin;
    private final AbilityProtection protection;
    private final Map<String, Boolean> hooks = new LinkedHashMap<>();
    private final Map<String, Boolean> failedHandlers = new java.util.concurrent.ConcurrentHashMap<>();
    private Method mythicInstance;
    private Method mythicMobManager;
    private Method mythicGetActiveMob;

    public AbilityHookManager(GPSafeGuardPlugin plugin, AbilityProtection protection) {
        this.plugin = plugin;
        this.protection = protection;
    }

    public void start() {
        stop();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Plugin lib = enabledPlugin("MythicLib");
        if (lib != null) {
            register(lib, "mythiclib", "io.lumine.mythic.lib.api.event.skill.PlayerCastSkillEvent",
                    EventPriority.LOWEST, this::onOptionalCastGuard, "getPlayer");
            register(lib, "mythiclib", "io.lumine.mythic.lib.api.event.PlayerAttackEvent",
                    EventPriority.LOWEST, this::onPlayerAttack, "getAttacker", "getEntity", "getDamage", "toBukkit");
            register(lib, "mythiclib", "io.lumine.mythic.lib.api.event.PlayerAttackEvent",
                    EventPriority.HIGHEST, this::onPlayerAttack, "getAttacker", "getEntity", "getDamage", "toBukkit");
            register(lib, "mythiclib", "io.lumine.mythic.lib.api.event.OnHitEffectEvent",
                    EventPriority.LOWEST, this::onHitEffect, "getPlayer", "getBukkitEvent");
            observeTargets(lib, "mythiclib", "io.lumine.mythic.lib.api.event.skill.PlayerCastSkillEvent", event -> {
                Player player = AbilitySourceResolver.player(AbilitySourceResolver.read(event, "getPlayer", null));
                Object result = AbilitySourceResolver.read(event, "getResult", null);
                Object metadata = AbilitySourceResolver.read(result, "getMythicMobsSkillMetadata", null);
                if (metadata == null) metadata = AbilitySourceResolver.read(result, "getMythicMobskillMetadata", null);
                filterMythicTargets(metadata, player, event);
            });
        }
        Plugin items = enabledPlugin("MMOItems");
        if (items != null) hooks.put("MMOItems target damage via MythicLib", lib != null);
        Plugin mobs = enabledPlugin("MythicMobs");
        if (mobs != null) {
            register(mobs, "mythicmobs", "io.lumine.mythic.bukkit.events.MythicDamageEvent",
                    EventPriority.HIGHEST, this::onMythicDamage, "getTarget|target", "getCaster|caster");
            register(mobs, "mythicmobs", "io.lumine.mythic.bukkit.events.MythicHealMechanicEvent",
                    EventPriority.HIGHEST, this::onMythicHeal, "getTarget", "getEntity");
            observeTargets(mobs, "mythicmobs", "io.lumine.mythic.bukkit.events.MythicTriggerEvent", event -> {
                Object metadata = AbilitySourceResolver.read(event, "getSkillMetadata", "skillMetadata");
                if (metadata == null) metadata = AbilitySourceResolver.read(event, "getMetadata", "skillMeta");
                Player player = AbilitySourceResolver.player(AbilitySourceResolver.read(metadata, "getCaster", null));
                filterMythicTargets(metadata, player, event);
            });
            hooks.put("MythicMobs owner", false);
            try {
                Class<?> mythic = load(mobs, "io.lumine.mythic.bukkit.MythicBukkit");
                mythicInstance = mythic.getMethod("inst");
                mythicMobManager = mythic.getMethod("getMobManager");
                mythicGetActiveMob = mythicMobManager.getReturnType().getMethod("getActiveMob", java.util.UUID.class);
                hooks.put("MythicMobs owner", true);
            } catch (ReflectiveOperationException | LinkageError ex) {
                warn("MythicMobs owner", ex);
            }
        }
        plugin.getLogger().info("Ability hooks: " + describe());
    }

    private Plugin enabledPlugin(String name) {
        Plugin found = plugin.getServer().getPluginManager().getPlugin(name);
        return found != null && found.isEnabled() ? found : null;
    }

    private Class<?> load(Plugin owner, String name) throws ClassNotFoundException {
        return Class.forName(name, true, owner.getClass().getClassLoader());
    }

    /** Filter explicit Mythic targets without ever cancelling the player's cast. */
    private void observeTargets(Plugin owner, String setting, String className, Consumer<Event> handler) {
        String name = className.substring(className.lastIndexOf('.') + 1) + " target filter";
        hooks.put(name, false);
        try {
            Class<? extends Event> type = load(owner, className).asSubclass(Event.class);
            if (className.endsWith("MythicTriggerEvent") && !hasAccessor(type, "getSkillMetadata|skillMetadata")
                    && !hasAccessor(type, "getMetadata|skillMeta")) {
                throw new NoSuchMethodException("No accessible Mythic skill metadata");
            }
            plugin.getServer().getPluginManager().registerEvent(type, this, EventPriority.HIGHEST, (listener, event) -> {
                if (!type.isInstance(event) || event.isAsynchronous() || !plugin.getConfigManager().isAbilityProtectionEnabled()
                        || !plugin.getConfigManager().isAbilityHookEnabled(setting)) return;
                try {
                    handler.accept(event);
                } catch (RuntimeException | LinkageError ex) {
                    hooks.put(name, false);
                    if (failedHandlers.putIfAbsent(name, true) == null) warn(name, ex);
                }
            }, plugin, true);
            hooks.put(name, true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            warn(name, ex);
        }
    }

    private void filterMythicTargets(Object metadata, Player player, Event event) {
        if (metadata == null) return;
        Object targets = AbilitySourceResolver.read(metadata, "getEntityTargets", null);
        if (!(targets instanceof java.util.Collection<?> collection)) return;
        java.util.List<Object> allowed = new java.util.ArrayList<>();
        for (Object target : collection) {
            Entity entity = AbilitySourceResolver.entity(target);
            if (entity != null && protection.shouldBlock(player, entity, event)) {
                protection.blocked(player, "Mythic explicit target removed (cast allowed)", entity);
            } else {
                allowed.add(target);
            }
        }
        if (allowed.size() == collection.size()) return;
        try {
            metadata.getClass().getMethod("setEntityTargets", java.util.Collection.class).invoke(metadata, allowed);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot replace Mythic targets", ex);
        }
    }

    private void register(Plugin owner, String setting, String className, EventPriority priority,
                          Consumer<Event> handler, String... accessors) {
        String name = className.substring(className.lastIndexOf('.') + 1) + " (" + priority + ")";
        hooks.put(name, false);
        try {
            Class<?> raw = load(owner, className);
            if (!Event.class.isAssignableFrom(raw) || !Cancellable.class.isAssignableFrom(raw)) {
                throw new IllegalArgumentException("Event tidak cancellable: " + className);
            }
            for (String accessor : accessors) {
                if (!hasAccessor(raw, accessor)) throw new NoSuchMethodException(className + " " + accessor);
            }
            Class<? extends Event> eventClass = raw.asSubclass(Event.class);
            plugin.getServer().getPluginManager().registerEvent(eventClass, this, priority, (listener, event) -> {
                // MythicLib AttackEvent and PlayerAttackEvent may share a HandlerList.
                if (!eventClass.isInstance(event) || !plugin.getConfigManager().isAbilityProtectionEnabled()
                        || !plugin.getConfigManager().isAbilityHookEnabled(setting)
                        || ((Cancellable) event).isCancelled()) return;
                // Bukkit entity and GP APIs are main-thread-only.
                if (event.isAsynchronous()) {
                    ((Cancellable) event).setCancelled(true);
                    if (failedHandlers.putIfAbsent(name + " async", true) == null) {
                        plugin.getLogger().warning(name + " asynchronous dibatalkan: pemeriksaan claim memerlukan main thread.");
                    }
                    return;
                }
                try {
                    handler.accept(event);
                } catch (RuntimeException | LinkageError ex) {
                    // An incompatible attack hook must not disable ordinary farm hits.
                    if (!className.endsWith("PlayerAttackEvent")) ((Cancellable) event).setCancelled(true);
                    hooks.put(name, false);
                    if (failedHandlers.putIfAbsent(name, true) == null) warn(name + " runtime", ex);
                }
            }, plugin, true);
            hooks.put(name, true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            warn(name, ex);
        }
    }

    private boolean hasAccessor(Class<?> type, String specification) {
        String[] parts = specification.split("\\|");
        try {
            type.getMethod(parts[0]);
            return true;
        } catch (NoSuchMethodException ignored) {
        }
        if (parts.length > 1) {
            for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
                try {
                    Field field = parent.getDeclaredField(parts[1]);
                    return !Modifier.isStatic(field.getModifiers()) && field.trySetAccessible();
                } catch (NoSuchFieldException ignored) {
                }
            }
        }
        return false;
    }

    private void onPlayerAttack(Event event) {
        Player player = AbilitySourceResolver.player(AbilitySourceResolver.read(event, "getAttacker", null));
        Entity target = AbilitySourceResolver.entity(AbilitySourceResolver.read(event, "getEntity", null));
        Object bukkit = AbilitySourceResolver.read(event, "toBukkit", null);
        protection.rememberAttacker(bukkit, player);
        Object damage = AbilitySourceResolver.read(event, "getDamage", null);
        Object types = AbilitySourceResolver.read(damage, "collectTypes", null);
        if (!(types instanceof java.util.Collection<?> collected)) {
            // Unknown metadata must not turn every melee attack into a protected skill.
            if (failedHandlers.putIfAbsent("damage types", true) == null) {
                plugin.getLogger().warning("MythicLib collectTypes unavailable; using Bukkit skill causes only.");
            }
            return;
        }
        boolean skill = collected.stream().anyMatch(type -> java.util.Set.of("SKILL", "ON_HIT", "DOT", "MINION").contains(type.toString()));
        protection.markSkill(bukkit, skill);
        if (!skill || !protection.shouldBlock(player, target, event)) return;
        // Mixed weapon + skill metadata: strip skill packets, preserve ordinary weapon damage.
        Object packets = AbilitySourceResolver.read(damage, "getPackets", null);
        if (packets instanceof java.util.Collection<?> list) {
            list.removeIf(packet -> {
                Object packetTypes = AbilitySourceResolver.read(packet, "getTypes", null);
                return packetTypes instanceof java.util.Collection<?> entries && entries.stream().anyMatch(
                        type -> java.util.Set.of("SKILL", "ON_HIT", "DOT", "MINION").contains(type.toString()));
            });
            if (!list.isEmpty()) {
                protection.markSkill(bukkit, false);
                protection.blocked(player, "MythicLib skill packets removed (weapon damage preserved)", target);
                return;
            }
        }
        blockDamage(event, player, target, "MythicLib skill attack");
    }

    private void onHitEffect(Event event) {
        Player player = AbilitySourceResolver.player(AbilitySourceResolver.read(event, "getPlayer", null));
        Object damage = AbilitySourceResolver.read(event, "getBukkitEvent", null);
        protection.rememberAttacker(damage, player);
        Entity target = AbilitySourceResolver.entity(AbilitySourceResolver.read(damage, "getEntity", null));
        // Cancel only the extra on-hit effect, never the underlying normal hit.
        blockDamage(event, player, target, "MythicLib on-hit effect");
    }

    private void onOptionalCastGuard(Event event) {
        if (!plugin.getConfigManager().isAbilityCastGuardEnabled()) return;
        Player player = AbilitySourceResolver.player(AbilitySourceResolver.read(event, "getPlayer", null));
        if (player == null) throw new IllegalStateException("Cannot resolve cast player");
        Object metadata = AbilitySourceResolver.read(event, "getMetadata", null);
        Entity explicit = AbilitySourceResolver.entity(AbilitySourceResolver.read(metadata, "getTargetEntityOrNull", null));
        if (protection.shouldBlockCast(player, event)
                || explicit != null && protection.shouldBlock(player, explicit, event)) {
            ((Cancellable) event).setCancelled(true);
            protection.blocked(player, "optional pre-cast guard", explicit);
        }
    }

    private void onMythicDamage(Event event) {
        Object caster = AbilitySourceResolver.read(event, "getCaster", "caster");
        Player player = AbilitySourceResolver.player(caster);
        if (player == null) player = resolveMythicOwner(AbilitySourceResolver.entity(caster));
        Entity target = AbilitySourceResolver.entity(AbilitySourceResolver.read(event, "getTarget", "target"));
        blockDamage(event, player, target, "MythicMobs damage");
    }

    private void onMythicHeal(Event event) {
        Object caster = AbilitySourceResolver.read(event, "getEntity", null);
        Player player = AbilitySourceResolver.player(caster);
        if (player == null) player = resolveMythicOwner(AbilitySourceResolver.entity(caster));
        Entity target = AbilitySourceResolver.entity(AbilitySourceResolver.read(event, "getTarget", null));
        blockDamage(event, player, target, "MythicMobs heal effect");
    }

    private void blockDamage(Event event, Player player, Entity target, String source) {
        if (target == null) throw new IllegalStateException("Target entity tidak dapat di-resolve: " + source);
        if (protection.shouldBlock(player, target, event)) {
            ((Cancellable) event).setCancelled(true);
            protection.blocked(player, source, target);
        }
    }

    private Object activeMythicMob(Entity entity) {
        if (entity == null || mythicInstance == null || mythicMobManager == null || mythicGetActiveMob == null
                || !plugin.getConfigManager().isAbilityHookEnabled("mythicmobs") || enabledPlugin("MythicMobs") == null) return null;
        try {
            Object manager = mythicMobManager.invoke(mythicInstance.invoke(null));
            Object result = mythicGetActiveMob.invoke(manager, entity.getUniqueId());
            return result instanceof java.util.Optional<?> optional ? optional.orElse(null) : result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            if (failedHandlers.putIfAbsent("MythicMobs lookup", true) == null) warn("MythicMobs lookup", ex);
            return null;
        }
    }
    public String mythicMobId(Entity entity) {
        Object type = AbilitySourceResolver.read(activeMythicMob(entity), "getType", null);
        Object id = AbilitySourceResolver.read(type, "getInternalName", null);
        return id == null ? null : id.toString();
    }
    public Player resolveMythicOwner(Entity entity) {
        if (entity == null || mythicInstance == null || mythicMobManager == null || mythicGetActiveMob == null
                || !plugin.getConfigManager().isAbilityHookEnabled("mythicmobs")
                || enabledPlugin("MythicMobs") == null) return null;
        try {
            Object manager = mythicMobManager.invoke(mythicInstance.invoke(null));
            return AbilitySourceResolver.player(mythicGetActiveMob.invoke(manager, entity.getUniqueId()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            hooks.put("MythicMobs owner", false);
            if (failedHandlers.putIfAbsent("MythicMobs owner", true) == null) warn("MythicMobs owner", ex);
            return null;
        }
    }

    public String describe() {
        if (hooks.isEmpty()) return "Bukkit fallback (plugin opsional tidak ditemukan)";
        return hooks.entrySet().stream().map(entry -> entry.getKey() + "="
                + (entry.getValue() ? "READY" : "UNAVAILABLE")).collect(java.util.stream.Collectors.joining(", "));
    }

    private void warn(String hook, Throwable ex) {
        plugin.getLogger().warning("Hook " + hook + " tidak kompatibel/tersedia: " + ex
                + ". Proteksi Bukkit tetap aktif; periksa versi plugin dan hasil testing skill.");
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        hooks.clear();
        failedHandlers.clear();
        mythicInstance = null;
        mythicMobManager = null;
        mythicGetActiveMob = null;
    }
}
