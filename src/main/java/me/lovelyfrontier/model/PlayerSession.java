package me.lovelyfrontier.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerSession {
    private String sessionId;
    private String idempotencyKey;
    private String dungeonId;
    private String difficulty;
    private UUID leaderUuid;
    private final List<UUID> partyMembers = new ArrayList<>();
    private boolean ticketConsumed;
    private String instanceId;
    private final java.util.Map<UUID, String> votes = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<UUID, Boolean> readyStatus = new java.util.concurrent.ConcurrentHashMap<>();
    private String portalId;
    private long expiresAt; // Epoch timestamp in ms
    private boolean bypassTicket = false;

    public PlayerSession() {}

    public PlayerSession(String sessionId, String idempotencyKey, String dungeonId, String difficulty,
                         UUID leaderUuid, List<UUID> partyMembers, long expiresAt) {
        this.sessionId = sessionId;
        this.idempotencyKey = idempotencyKey;
        this.dungeonId = dungeonId;
        this.difficulty = difficulty;
        this.leaderUuid = leaderUuid;
        this.partyMembers.addAll(partyMembers);
        this.ticketConsumed = false;
        this.instanceId = null;
        this.expiresAt = expiresAt;
    }

    public java.util.Map<UUID, String> getVotes() { return votes; }
    public java.util.Map<UUID, Boolean> getReadyStatus() { return readyStatus; }

    public String getPortalId() { return portalId; }
    public void setPortalId(String portalId) { this.portalId = portalId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getDungeonId() { return dungeonId; }
    public void setDungeonId(String dungeonId) { this.dungeonId = dungeonId; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public UUID getLeaderUuid() { return leaderUuid; }
    public void setLeaderUuid(UUID leaderUuid) { this.leaderUuid = leaderUuid; }

    public List<UUID> getPartyMembers() { return partyMembers; }

    public boolean isTicketConsumed() { return ticketConsumed; }
    public void setTicketConsumed(boolean ticketConsumed) { this.ticketConsumed = ticketConsumed; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public boolean isBypassTicket() { return bypassTicket; }
    public void setBypassTicket(boolean bypassTicket) { this.bypassTicket = bypassTicket; }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
