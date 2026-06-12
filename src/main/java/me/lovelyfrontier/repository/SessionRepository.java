package me.lovelyfrontier.repository;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.lock.RetryableTransaction;
import me.lovelyfrontier.model.PlayerSession;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class SessionRepository {

    private final LovelyFrontierPlugin plugin;
    private final DatabaseManager databaseManager;

    public SessionRepository(LovelyFrontierPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Saves a new player session to the database.
     */
    public CompletableFuture<Boolean> createSession(PlayerSession session) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO lf_sessions (session_id, idempotency_key, dungeon_id, difficulty, leader_uuid, party_members, ticket_consumed, instance_id, expires_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, session.getSessionId());
                ps.setString(2, session.getIdempotencyKey());
                ps.setString(3, session.getDungeonId());
                ps.setString(4, session.getDifficulty());
                ps.setString(5, session.getLeaderUuid().toString());
                ps.setString(6, serializeList(session.getPartyMembers()));
                ps.setBoolean(7, session.isTicketConsumed());
                ps.setString(8, session.getInstanceId());
                ps.setTimestamp(9, new Timestamp(session.getExpiresAt()));
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error creating session: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Retrieves a session by its idempotency key.
     */
    public CompletableFuture<PlayerSession> findByIdempotencyKey(String idempotencyKey) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM lf_sessions WHERE idempotency_key = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, idempotencyKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error finding session by idempotency key: " + e.getMessage());
            }
            return null;
        });
    }

    /**
     * Retrieves a session by its ID.
     */
    public CompletableFuture<PlayerSession> findSessionById(String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM lf_sessions WHERE session_id = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error finding session by ID: " + e.getMessage());
            }
            return null;
        });
    }

    /**
     * Updates ticket_consumed = TRUE and associates it with the created instance_id inside a transaction.
     */
    public CompletableFuture<Boolean> markConsumed(String sessionId, String instanceId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                return RetryableTransaction.execute(conn, () -> {
                    // Documenting multi-table transaction rule: R-005 lock order: 3. lf_sessions, 4. lf_instances
                    String selectSql = databaseManager.isMySQL()
                        ? "SELECT ticket_consumed FROM lf_sessions WHERE session_id = ? FOR UPDATE"
                        : "SELECT ticket_consumed FROM lf_sessions WHERE session_id = ?";
                    
                    boolean alreadyConsumed = false;
                    try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                        ps.setString(1, sessionId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                alreadyConsumed = rs.getBoolean("ticket_consumed");
                            }
                        }
                    }

                    if (alreadyConsumed) {
                        return false;
                    }

                    String updateSql = "UPDATE lf_sessions SET ticket_consumed = TRUE, instance_id = ? WHERE session_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setString(1, instanceId);
                        ps.setString(2, sessionId);
                        ps.executeUpdate();
                    }
                    return true;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Error marking session consumed: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Deletes all expired sessions.
     */
    public CompletableFuture<Integer> expireSessions() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM lf_sessions WHERE expires_at < ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                return ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error expiring sessions: " + e.getMessage());
                return 0;
            }
        });
    }

    // Helper to serialize UUID list to comma separated string
    private String serializeList(List<UUID> list) {
        return list.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    // Helper to deserialize UUID list from comma separated string
    private List<UUID> deserializeList(String str) {
        List<UUID> list = new ArrayList<>();
        if (str == null || str.trim().isEmpty()) {
            return list;
        }
        for (String s : str.split(",")) {
            try {
                list.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {}
        }
        return list;
    }

    // Helper to map ResultSet row to PlayerSession model
    private PlayerSession mapRow(ResultSet rs) throws SQLException {
        PlayerSession session = new PlayerSession(
                rs.getString("session_id"),
                rs.getString("idempotency_key"),
                rs.getString("dungeon_id"),
                rs.getString("difficulty"),
                UUID.fromString(rs.getString("leader_uuid")),
                deserializeList(rs.getString("party_members")),
                rs.getTimestamp("expires_at").getTime()
        );
        session.setTicketConsumed(rs.getBoolean("ticket_consumed"));
        session.setInstanceId(rs.getString("instance_id"));
        return session;
    }
}
