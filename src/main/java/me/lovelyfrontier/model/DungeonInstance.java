package me.lovelyfrontier.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DungeonInstance {
    private final String instanceId;
    private final String dungeonId;
    private final String difficulty;
    private final String worldName;
    private InstanceState state;
    private boolean bossCleared;
    private double bossHpPercent;
    private final long createdAt;

    // Track active players and disconnected players with their disconnect timestamp
    private final Set<UUID> members = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Long> disconnectedMembers = new ConcurrentHashMap<>();

    // Keep track of who initiated kicks
    private final Set<UUID> kickInitiators = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Double> bossBaseMaxHealth = new ConcurrentHashMap<>();

    public DungeonInstance(String instanceId, String dungeonId, String difficulty, String worldName) {
        this.instanceId = instanceId;
        this.dungeonId = dungeonId;
        this.difficulty = difficulty;
        this.worldName = worldName;
        this.state = InstanceState.LOADING;
        this.bossCleared = false;
        this.bossHpPercent = 1.0;
        this.createdAt = System.currentTimeMillis();
    }

    public String getInstanceId() { return instanceId; }
    public String getDungeonId() { return dungeonId; }
    public String getDifficulty() { return difficulty; }
    public String getWorldName() { return worldName; }

    public synchronized InstanceState getState() { return state; }
    public synchronized void setState(InstanceState state) { this.state = state; }

    public synchronized boolean isBossCleared() { return bossCleared; }
    public synchronized void setBossCleared(boolean bossCleared) { this.bossCleared = bossCleared; }

    public synchronized double getBossHpPercent() { return bossHpPercent; }
    public synchronized void setBossHpPercent(double bossHpPercent) { this.bossHpPercent = bossHpPercent; }

    public long getCreatedAt() { return createdAt; }

    public Set<UUID> getMembers() { return members; }
    public Map<UUID, Long> getDisconnectedMembers() { return disconnectedMembers; }
    public Set<UUID> getKickInitiators() { return kickInitiators; }
    public Map<UUID, Double> getBossBaseMaxHealth() { return bossBaseMaxHealth; }

    public synchronized boolean isPlayerInGracePeriod(UUID uuid) {
        return disconnectedMembers.containsKey(uuid);
    }
}
