package com.gptg.safeguard.trust;

import com.gptg.safeguard.GPSafeGuardPlugin;
import com.gptg.safeguard.util.BedrockUtil;
import com.gptg.safeguard.util.GriefPreventionBridge;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TrustManager {

    private final GPSafeGuardPlugin plugin;

    /** target playerId -> daftar invite yang sedang pending, urutan lama->baru. */
    private final Map<UUID, Deque<TrustInvite>> pendingByTarget = new HashMap<>();

    public TrustManager(GPSafeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    /** Dipanggil saat inviter menjalankan /trust, /accesstrust, /containertrust, atau /permissiontrust. */
    public void startInvite(Player inviter, Player target, TrustType type) {
        if (inviter.getUniqueId().equals(target.getUniqueId())) {
            plugin.getMessages().send(inviter, "trust.cannot-trust-self");
            return;
        }

        Claim claim = GriefPreventionBridge.getClaimAt(inviter.getLocation());
        if (claim == null) {
            plugin.getMessages().send(inviter, "trust.not-in-claim");
            return;
        }
        if (!GriefPreventionBridge.canManageClaim(inviter, claim)) {
            plugin.getMessages().send(inviter, "trust.no-claim-permission");
            return;
        }

        if (plugin.getConfigManager().isSkipIfAlreadyTrusted()
                && GriefPreventionBridge.alreadyHasTrust(claim, target.getUniqueId(), type)) {
            plugin.getMessages().send(inviter, "trust.already-trusted", Map.of(
                    "target", target.getName(),
                    "trust_type", type.displayName()
            ));
            return;
        }

        // Ganti invite lama dari inviter yang sama ke target yang sama (kalau ada).
        removeInvite(target.getUniqueId(), inviter.getName());

        TrustInvite invite = new TrustInvite(inviter.getUniqueId(), inviter.getName(),
                target.getUniqueId(), target.getName(), type, claim);

        pendingByTarget.computeIfAbsent(target.getUniqueId(), k -> new ArrayDeque<>()).addLast(invite);

        int timeout = plugin.getConfigManager().getTrustTimeoutSeconds();

        Map<String, String> vars = Map.of(
                "inviter", inviter.getName(),
                "target", target.getName(),
                "trust_type", type.displayName(),
                "time", String.valueOf(timeout)
        );

        plugin.getMessages().send(inviter, "trust.invite-sent", vars);

        if (BedrockUtil.isBedrock(target)) {
            plugin.getMessages().send(target, "trust.invite-received-bedrock", vars);
        } else {
            plugin.getMessages().send(target, "trust.invite-received", vars);
        }

        var task = Bukkit.getScheduler().runTaskLater(plugin, () -> expireInvite(invite), timeout * 20L);
        invite.setExpiryTask(task);

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[debug] Trust invite dibuat: " + inviter.getName() + " -> "
                    + target.getName() + " (" + type + "), timeout " + timeout + "s");
        }
    }

    private void expireInvite(TrustInvite invite) {
        Deque<TrustInvite> deque = pendingByTarget.get(invite.getTargetId());
        if (deque == null || !deque.remove(invite)) return; // sudah di-accept/decline duluan
        if (deque.isEmpty()) pendingByTarget.remove(invite.getTargetId());

        Map<String, String> vars = Map.of(
                "inviter", invite.getInviterName(),
                "target", invite.getTargetName(),
                "trust_type", invite.getType().displayName()
        );

        Player target = Bukkit.getPlayer(invite.getTargetId());
        if (target != null) plugin.getMessages().send(target, "trust.expired-target", vars);

        Player inviter = Bukkit.getPlayer(invite.getInviterId());
        if (inviter != null) plugin.getMessages().send(inviter, "trust.expired-inviter", vars);
    }

    /** /trust accept [player] */
    public void accept(Player target, String inviterNameOrNull) {
        TrustInvite invite = takeInvite(target.getUniqueId(), inviterNameOrNull);
        if (invite == null) {
            sendNoPendingInvite(target, inviterNameOrNull);
            return;
        }
        invite.cancelExpiry();

        GriefPreventionBridge.grantTrust(invite.getClaim(), target.getUniqueId(), invite.getType());

        Map<String, String> vars = Map.of(
                "inviter", invite.getInviterName(),
                "target", invite.getTargetName(),
                "trust_type", invite.getType().displayName()
        );
        plugin.getMessages().send(target, "trust.accepted-target", vars);

        Player inviter = Bukkit.getPlayer(invite.getInviterId());
        if (inviter != null) plugin.getMessages().send(inviter, "trust.accepted-inviter", vars);
    }

    /** /trust decline [player] */
    public void decline(Player target, String inviterNameOrNull) {
        TrustInvite invite = takeInvite(target.getUniqueId(), inviterNameOrNull);
        if (invite == null) {
            sendNoPendingInvite(target, inviterNameOrNull);
            return;
        }
        invite.cancelExpiry();

        Map<String, String> vars = Map.of(
                "inviter", invite.getInviterName(),
                "target", invite.getTargetName(),
                "trust_type", invite.getType().displayName()
        );
        plugin.getMessages().send(target, "trust.declined-target", vars);

        Player inviter = Bukkit.getPlayer(invite.getInviterId());
        if (inviter != null) plugin.getMessages().send(inviter, "trust.declined-inviter", vars);
    }

    private void sendNoPendingInvite(Player target, String inviterNameOrNull) {
        if (inviterNameOrNull != null) {
            plugin.getMessages().send(target, "trust.no-pending-invite", Map.of("target", inviterNameOrNull));
        } else {
            plugin.getMessages().send(target, "trust.no-pending-invite-any");
        }
    }

    /** Ambil (dan hapus dari antrian) invite untuk target. Jika inviterName null, ambil yang PALING BARU. */
    private TrustInvite takeInvite(UUID targetId, String inviterNameOrNull) {
        Deque<TrustInvite> deque = pendingByTarget.get(targetId);
        if (deque == null || deque.isEmpty()) return null;

        TrustInvite found;
        if (inviterNameOrNull == null) {
            found = deque.peekLast();
        } else {
            found = deque.stream()
                    .filter(i -> i.getInviterName().equalsIgnoreCase(inviterNameOrNull))
                    .reduce((first, second) -> second) // ambil yang paling baru jika ada duplikat
                    .orElse(null);
        }
        if (found != null) {
            deque.remove(found);
            if (deque.isEmpty()) pendingByTarget.remove(targetId);
        }
        return found;
    }

    private void removeInvite(UUID targetId, String inviterName) {
        Deque<TrustInvite> deque = pendingByTarget.get(targetId);
        if (deque == null) return;
        deque.removeIf(i -> {
            if (i.getInviterName().equalsIgnoreCase(inviterName)) {
                i.cancelExpiry();
                return true;
            }
            return false;
        });
        if (deque.isEmpty()) pendingByTarget.remove(targetId);
    }

    /** Bersihkan semua invite (mis. saat plugin disable) tanpa mengirim pesan. */
    public void clearAll() {
        pendingByTarget.values().forEach(deque -> deque.forEach(TrustInvite::cancelExpiry));
        pendingByTarget.clear();
    }
}
