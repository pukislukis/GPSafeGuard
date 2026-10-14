package com.gptg.safeguard.protection;

import org.bukkit.Bukkit;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Explicit accessor paths only: never guess an attacker from nearby players. */
public final class AbilitySourceResolver {
    private AbilitySourceResolver() {}

    public static Object read(Object object, String method, String field) {
        if (object == null) return null;
        try {
            Method getter = object.getClass().getMethod(method);
            return getter.invoke(object);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Some MythicMobs releases expose event data as fields rather than getters.
        }
        if (field != null) {
            for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Field member = type.getDeclaredField(field);
                    if (member.trySetAccessible()) return member.get(object);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
        }
        return null;
    }

    public static Entity entity(Object source) {
        return entity(source, 0);
    }

    private static Entity entity(Object source, int depth) {
        if (source == null || depth > 6) return null;
        if (source instanceof Entity entity) return entity;
        Object bukkit = read(source, "getBukkitEntity", null);
        if (bukkit instanceof Entity entity) return entity;
        Object wrapped = read(source, "getEntity", null);
        return wrapped == source ? null : entity(wrapped, depth + 1);
    }

    public static Player player(Object source) {
        return player(source, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private static Player player(Object source, Set<Object> visited, int depth) {
        if (source == null || depth > 8 || !visited.add(source)) return null;
        if (source instanceof Player player) return player;
        if (source instanceof UUID uuid) return Bukkit.getPlayer(uuid);
        if (source instanceof Optional<?> optional) return player(optional.orElse(null), visited, depth + 1);
        if (source instanceof Projectile projectile) return player(projectile.getShooter(), visited, depth + 1);
        if (source instanceof AreaEffectCloud cloud) return player(cloud.getSource(), visited, depth + 1);
        if (source instanceof TNTPrimed tnt) return player(tnt.getSource(), visited, depth + 1);
        if (source instanceof Tameable tameable) return player(tameable.getOwner(), visited, depth + 1);
        for (String accessor : new String[]{"getPlayer", "getBukkitEntity", "getEntity", "getCaster", "getAttacker", "getOwner", "getParent"}) {
            String field = accessor.equals("getOwner") ? "owner" : null;
            Player result = player(read(source, accessor, field), visited, depth + 1);
            if (result != null) return result;
        }
        return null;
    }
}
