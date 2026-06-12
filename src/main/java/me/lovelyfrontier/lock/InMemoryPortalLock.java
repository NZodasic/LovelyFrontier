package me.lovelyfrontier.lock;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPortalLock {
    private final ConcurrentHashMap<String, UUID> locks = new ConcurrentHashMap<>();

    /**
     * Tries to acquire the lock for a given portalId.
     * Returns true if the lock was acquired, or if the current leader already holds the lock.
     * Returns false if the lock is held by another party leader.
     */
    public boolean tryLock(String portalId, UUID leaderUuid) {
        UUID existing = locks.putIfAbsent(portalId, leaderUuid);
        return existing == null || existing.equals(leaderUuid);
    }

    /**
     * Unlocks the given portal.
     */
    public void unlock(String portalId) {
        locks.remove(portalId);
    }

    /**
     * Checks who owns the lock for a given portalId.
     */
    public UUID getLockOwner(String portalId) {
        return locks.get(portalId);
    }
}
