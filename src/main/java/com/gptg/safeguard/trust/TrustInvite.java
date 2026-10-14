package com.gptg.safeguard.trust;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/** Menyimpan satu undangan trust yang sedang menunggu accept/decline. */
public class TrustInvite {

    private final UUID inviterId;
    private final String inviterName;
    private final UUID targetId;
    private final String targetName;
    private final TrustType type;
    private final Claim claim;
    private final long createdAtMillis;
    private BukkitTask expiryTask;

    public TrustInvite(UUID inviterId, String inviterName, UUID targetId, String targetName,
                        TrustType type, Claim claim) {
        this.inviterId = inviterId;
        this.inviterName = inviterName;
        this.targetId = targetId;
        this.targetName = targetName;
        this.type = type;
        this.claim = claim;
        this.createdAtMillis = System.currentTimeMillis();
    }

    public UUID getInviterId() { return inviterId; }
    public String getInviterName() { return inviterName; }
    public UUID getTargetId() { return targetId; }
    public String getTargetName() { return targetName; }
    public TrustType getType() { return type; }
    public Claim getClaim() { return claim; }
    public long getCreatedAtMillis() { return createdAtMillis; }

    public void setExpiryTask(BukkitTask task) { this.expiryTask = task; }

    public void cancelExpiry() {
        if (expiryTask != null && !expiryTask.isCancelled()) {
            expiryTask.cancel();
        }
    }
}
