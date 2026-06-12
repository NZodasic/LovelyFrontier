package me.lovelyfrontier.manager;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.PlayerSession;
import me.lovelyfrontier.repository.SessionRepository;
import me.lovelyfrontier.saga.DungeonCreationSaga;
import me.lovelyfrontier.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SessionManager {

    private final LovelyFrontierPlugin plugin;
    private final SessionRepository sessionRepository;
    private final Map<String, PlayerSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> sessionTasks = new ConcurrentHashMap<>();

    // Guava cache for idempotency key -> creation result (instanceId)
    private final Cache<String, SessionResult> idempotencyCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    public SessionManager(LovelyFrontierPlugin plugin, SessionRepository sessionRepository) {
        this.plugin = plugin;
        this.sessionRepository = sessionRepository;

        // Schedule periodic cleanup of expired sessions from DB and memory
        BukkitSchedulerTask();
    }

    private void BukkitSchedulerTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            sessionRepository.expireSessions().thenAccept(count -> {
                if (count > 0) {
                    plugin.getLogger().info("Expired " + count + " sessions from the database.");
                }
            });
            // Clean up in-memory map
            activeSessions.values().removeIf(session -> {
                if (session.isExpired()) {
                    cleanupLocks(session);
                    return true;
                }
                return false;
            });
        }, 1200L, 1200L); // Every 60 seconds (1200 ticks)
    }

    /**
     * Creates a validated session for dungeon entry.
     */
    public CompletableFuture<PlayerSession> createSession(String dungeonId, String difficulty, UUID leaderUuid, List<UUID> partyMembers, String portalId) {
        if (partyMembers.size() <= 1 && !plugin.getConfigManager().isPartyAllowSolo()) {
            Player leader = Bukkit.getPlayer(leaderUuid);
            if (leader != null && leader.isOnline()) {
                leader.sendMessage(MessageUtil.get("solo_not_allowed"));
            }
            if (portalId != null) {
                plugin.getInMemoryPortalLock().unlock(portalId);
                plugin.getPortalRepository().releaseLock(portalId);
            }
            return CompletableFuture.completedFuture(null);
        }

        String sessionId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();
        long expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2); // 2 minutes expiry

        PlayerSession session = new PlayerSession(sessionId, idempotencyKey, dungeonId, difficulty, leaderUuid, partyMembers, expiresAt);
        session.setPortalId(portalId);
        activeSessions.put(sessionId, session);

        return sessionRepository.createSession(session).thenApply(success -> {
            if (success) {
                // Start voting phase
                startVotePhase(session);
                return session;
            } else {
                activeSessions.remove(sessionId);
                return null;
            }
        });
    }

    public PlayerSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    public PlayerSession findActiveSessionByPlayer(UUID playerUuid) {
        for (PlayerSession session : activeSessions.values()) {
            if (session.getPartyMembers().contains(playerUuid)) {
                return session;
            }
        }
        return null;
    }

    public void removeSession(String sessionId) {
        activeSessions.remove(sessionId);
        BukkitTask task = sessionTasks.remove(sessionId);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Starts the difficulty voting phase.
     */
    private void startVotePhase(PlayerSession session) {
        // If solo, auto-pass vote and confirm phases directly to saga (R-004)
        if (session.getPartyMembers().size() <= 1) {
            startDungeon(session.getSessionId()).thenAccept(res -> {
                if (!res.isSuccess()) {
                    cancelSession(session.getSessionId(), "Solo creation failed: " + res.getMessage());
                }
            });
            return;
        }

        // Broadcast vote call
        broadcastToParty(session, MessageUtil.get("party_vote_broadcast", "difficulty", session.getDifficulty()));

        // Set a 60-second timeout task for the vote phase (Spec Section 4)
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cancelSession(session.getSessionId(), "Difficulty vote timed out.");
        }, 1200L); // 60 seconds (1200 ticks)
        sessionTasks.put(session.getSessionId(), timeoutTask);
    }

    /**
     * Submits a difficulty vote for a member.
     */
    public void submitVote(String sessionId, UUID playerUuid, String vote) {
        PlayerSession session = activeSessions.get(sessionId);
        if (session == null) return;

        session.getVotes().put(playerUuid, vote.toLowerCase());
        broadcastToParty(session, MessageUtil.get("party_voted", "player", Bukkit.getOfflinePlayer(playerUuid).getName(), "vote", vote));

        // Check if vote is resolved
        int totalMembers = session.getPartyMembers().size();
        int yesCount = 0;
        int noCount = 0;

        for (String v : session.getVotes().values()) {
            if (v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("accept") || v.equalsIgnoreCase(session.getDifficulty())) {
                yesCount++;
            } else if (v.equalsIgnoreCase("no") || v.equalsIgnoreCase("decline")) {
                noCount++;
            }
        }

        int majorityRequired = (totalMembers / 2) + 1;

        if (yesCount >= majorityRequired) {
            // Majority accepted, cancel vote timer and proceed to confirm phase
            BukkitTask task = sessionTasks.remove(sessionId);
            if (task != null) task.cancel();
            startConfirmPhase(session);
        } else if (noCount >= majorityRequired || (session.getVotes().size() == totalMembers && yesCount < majorityRequired)) {
            // Majority declined
            cancelSession(sessionId, "Difficulty vote rejected by party.");
        }
    }

    /**
     * Starts the party pre-confirmation phase.
     */
    private void startConfirmPhase(PlayerSession session) {
        broadcastToParty(session, MessageUtil.get("party_confirm_broadcast"));

        // Set a 180-second timeout task for the confirmation phase (Spec Section 4)
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cancelSession(session.getSessionId(), "Ready confirmation timed out.");
        }, 3600L); // 180 seconds (3600 ticks)
        sessionTasks.put(session.getSessionId(), timeoutTask);
    }

    /**
     * Submits a ready confirmation.
     */
    public void submitReady(String sessionId, UUID playerUuid, boolean ready) {
        PlayerSession session = activeSessions.get(sessionId);
        if (session == null) return;

        if (!ready) {
            cancelSession(sessionId, "Ready confirmation declined by " + Bukkit.getOfflinePlayer(playerUuid).getName());
            return;
        }

        session.getReadyStatus().put(playerUuid, true);
        broadcastToParty(session, MessageUtil.get("party_ready", "player", Bukkit.getOfflinePlayer(playerUuid).getName()));

        // Check if all party members are ready
        boolean allReady = true;
        for (UUID member : session.getPartyMembers()) {
            if (!session.getReadyStatus().getOrDefault(member, false)) {
                allReady = false;
                break;
            }
        }

        if (allReady) {
            // Cancel confirmation timer
            BukkitTask task = sessionTasks.remove(sessionId);
            if (task != null) task.cancel();

            // Run dungeon start
            startDungeon(sessionId).thenAccept(res -> {
                if (!res.isSuccess()) {
                    cancelSession(sessionId, "Saga creation failed: " + res.getMessage());
                }
            });
        }
    }

    /**
     * Aborts the session, unlocks portal, and notifies players.
     */
    private String translateReason(String reason) {
        if (reason == null) return "Lỗi không xác định.";
        if (reason.contains("Saga execution failed")) {
            return "Không thể khởi tạo phụ bản (Lỗi tạo thế giới hoặc schematic).";
        }
        if (reason.contains("Solo creation failed")) {
            return "Tạo phụ bản đơn thất bại: " + translateReason(reason.substring(reason.indexOf(":") + 1).trim());
        }
        if (reason.contains("Saga creation failed")) {
            return "Tạo phụ bản nhóm thất bại: " + translateReason(reason.substring(reason.indexOf(":") + 1).trim());
        }
        if (reason.contains("Difficulty vote timed out")) {
            return "Quá thời gian bỏ phiếu độ khó.";
        }
        if (reason.contains("Difficulty vote rejected by party")) {
            return "Bầu chọn độ khó bị từ chối bởi tổ đội.";
        }
        if (reason.contains("Ready confirmation timed out")) {
            return "Quá thời gian xác nhận sẵn sàng.";
        }
        if (reason.contains("Ready confirmation declined by")) {
            return "Xác nhận sẵn sàng bị từ chối bởi " + reason.substring(reason.indexOf("by") + 2).trim();
        }
        if (reason.contains("Failed to create DB session")) {
            return "Không thể tạo phiên kết nối cơ sở dữ liệu.";
        }
        if (reason.contains("Session not found or expired")) {
            return "Phiên làm việc không tồn tại hoặc đã hết hạn.";
        }
        return reason;
    }

    public void cancelSession(String sessionId, String reason) {
        PlayerSession session = activeSessions.remove(sessionId);
        if (session == null) return;

        BukkitTask task = sessionTasks.remove(sessionId);
        if (task != null) task.cancel();

        // Unlock portal
        cleanupLocks(session);

        // Notify party
        broadcastToParty(session, MessageUtil.get("session_cancelled", "reason", translateReason(reason)));
    }

    private void cleanupLocks(PlayerSession session) {
        if (session.getPortalId() != null) {
            plugin.getInMemoryPortalLock().unlock(session.getPortalId());
            plugin.getPortalRepository().releaseLock(session.getPortalId());
        }
    }

    private void broadcastToParty(PlayerSession session, String message) {
        for (UUID uuid : session.getPartyMembers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Attempts to start a dungeon instance for a given session.
     * Enforces idempotency checks and delegates creation to DungeonCreationSaga.
     */
    public CompletableFuture<SessionResult> startDungeon(String sessionId) {
        PlayerSession session = activeSessions.get(sessionId);
        if (session == null) {
            // Session expired or not found in memory, try loading from DB
            return sessionRepository.findSessionById(sessionId).thenCompose(dbSession -> {
                if (dbSession == null || dbSession.isExpired()) {
                    return CompletableFuture.completedFuture(new SessionResult(null, false, "Session not found or expired."));
                }
                return proceedWithSession(dbSession);
            });
        }
        return proceedWithSession(session);
    }

    private CompletableFuture<SessionResult> proceedWithSession(PlayerSession session) {
        String idempotencyKey = session.getIdempotencyKey();

        // 1. Check Guava Cache first
        SessionResult cachedResult = idempotencyCache.getIfPresent(idempotencyKey);
        if (cachedResult != null) {
            return CompletableFuture.completedFuture(cachedResult);
        }

        // 2. Query DB to check if ticket already marked consumed (Rule R-006)
        return sessionRepository.findByIdempotencyKey(idempotencyKey).thenCompose(dbSession -> {
            if (dbSession != null && dbSession.isTicketConsumed()) {
                SessionResult result = new SessionResult(dbSession.getInstanceId(), true, "Instance already created (idempotency match).");
                idempotencyCache.put(idempotencyKey, result);
                return CompletableFuture.completedFuture(result);
            }

            // 3. Initiate DungeonCreationSaga
            DungeonCreationSaga saga = new DungeonCreationSaga(plugin, session);
            return saga.execute().thenApply(instanceId -> {
                if (instanceId != null) {
                    SessionResult result = new SessionResult(instanceId, true, "Success");
                    idempotencyCache.put(idempotencyKey, result);
                    activeSessions.remove(session.getSessionId());
                    return result;
                } else {
                    return new SessionResult(null, false, "Saga execution failed.");
                }
            });
        });
    }

    /**
     * Force starts a dungeon instance, bypassing tickets, voting, and confirmations.
     */
    public CompletableFuture<SessionResult> forceStartDungeon(String dungeonId, String difficulty, Player leader, List<UUID> partyMembers) {
        String sessionId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();
        long expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);

        PlayerSession session = new PlayerSession(sessionId, idempotencyKey, dungeonId, difficulty, leader.getUniqueId(), partyMembers, expiresAt);
        session.setBypassTicket(true);
        activeSessions.put(sessionId, session);

        return sessionRepository.createSession(session).thenCompose(success -> {
            if (success) {
                return startDungeon(sessionId);
            } else {
                activeSessions.remove(sessionId);
                return CompletableFuture.completedFuture(new SessionResult(null, false, "Failed to create DB session."));
            }
        });
    }

    public static class SessionResult {
        private final String instanceId;
        private final boolean success;
        private final String message;

        public SessionResult(String instanceId, boolean success, String message) {
            this.instanceId = instanceId;
            this.success = success;
            this.message = message;
        }

        public String getInstanceId() { return instanceId; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
