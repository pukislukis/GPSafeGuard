package com.gptg.safeguard.util;

import com.gptg.safeguard.trust.TrustType;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.UUID;

/**
 * Satu-satunya class yang boleh memanggil API internal GriefPrevention secara
 * langsung. Tujuannya: kalau suatu saat nama method/enum di GriefPrevention
 * berubah antar versi, kamu HANYA perlu memperbaiki file ini.
 *
 * Method-method di bawah mengasumsikan API publik GriefPrevention (package
 * me.ryanhamshire.GriefPrevention) seperti yang dipakai sejak versi 16.x:
 *   - GriefPrevention.instance.dataStore.getClaimAt(Location, boolean, Claim)
 *   - Claim#allowEdit(Player) -> String (null jika diizinkan, atau pesan error)
 *   - Claim#setPermission(String playerUUID, ClaimPermission level)
 *   - Claim#ownerID -> UUID
 *   - enum ClaimPermission { Access, Inventory, Build, Manage, ... }
 *
 * Jika versi GriefPrevention di server kamu berbeda, cek ulang signature ini
 * di source GriefPrevention (Claim.java / ClaimPermission.java) dan sesuaikan.
 */
public final class GriefPreventionBridge {

    private GriefPreventionBridge() {}

    /** Ambil claim di lokasi tertentu, atau null jika lokasi bukan bagian dari claim manapun. */
    public static Claim getClaimAt(Location location) {
        return GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
    }

    /** True jika player berhak mengatur (trust/build) claim tersebut (owner, admin, atau punya manage-trust). */
    public static boolean canManageClaim(Player player, Claim claim) {
        // allowEdit() mengembalikan null jika diizinkan, atau String pesan alasan jika ditolak.
        return claim.allowEdit(player) == null;
    }

    public static UUID getOwnerId(Claim claim) {
        return claim.ownerID;
    }

    /** Build trust is required to harm entities; respects subdivisions and GP permission hooks. */
    public static boolean canDamageEntities(Player player, Claim claim, Event event) {
        return claim.checkPermission(player, ClaimPermission.Build, event) == null;
    }

    private static ClaimPermission mapPermission(TrustType type) {
        return switch (type) {
            case ACCESS -> ClaimPermission.Access;
            case CONTAINER -> ClaimPermission.Inventory;
            case BUILD -> ClaimPermission.Build;
            case PERMISSION -> ClaimPermission.Manage;
        };
    }

    /** Benar-benar memberikan trust ke player di claim tsb (dipanggil setelah invite di-accept). */
    public static void grantTrust(Claim claim, UUID playerId, TrustType type) {
        claim.setPermission(playerId.toString(), mapPermission(type));
    }

    /**
     * Cek apakah player sudah memiliki level trust >= type di claim ini.
     * Implementasi sederhana: cukup andalkan hasil grantTrust bersifat idempotent,
     * jadi di sini kita hanya best-effort check lewat playerNameToClaims cache
     * tidak selalu tersedia publicly -> untuk kesederhanaan & kompatibilitas
     * lintas versi, method ini selalu return false (artinya: invite tetap akan
     * dikirim). Kamu bisa memperkaya ini dengan membaca claim.getPermissions()
     * / Map internal jika tersedia di versi GriefPrevention kamu.
     */
    public static boolean alreadyHasTrust(Claim claim, UUID playerId, TrustType type) {
        return false;
    }
}
