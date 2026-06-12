package me.lovelyfrontier.repository;

import me.lovelyfrontier.LovelyFrontierPlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CompletionRepository {

    private final LovelyFrontierPlugin plugin;
    private final DatabaseManager databaseManager;

    public CompletionRepository(LovelyFrontierPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Saves a completed dungeon instance clear run to the database.
     */
    public CompletableFuture<Boolean> saveCompletion(String dungeonId, String difficulty, int teamSize, int completionTimeSeconds) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO lf_completions (completion_id, dungeon_id, difficulty, team_size, completion_time_seconds, completed_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, dungeonId);
                ps.setString(3, difficulty);
                ps.setInt(4, teamSize);
                ps.setInt(5, completionTimeSeconds);
                ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving dungeon completion record: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Retrieves the top completion records for a dungeon and difficulty, ordered by time ascending.
     */
    public CompletableFuture<List<CompletionRecord>> getLeaderboard(String dungeonId, String difficulty, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<CompletionRecord> list = new ArrayList<>();
            String sql = "SELECT * FROM lf_completions WHERE dungeon_id = ? AND difficulty = ? " +
                    "ORDER BY completion_time_seconds ASC LIMIT ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dungeonId);
                ps.setString(2, difficulty);
                ps.setInt(3, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new CompletionRecord(
                                rs.getString("completion_id"),
                                rs.getString("dungeon_id"),
                                rs.getString("difficulty"),
                                rs.getInt("team_size"),
                                rs.getInt("completion_time_seconds"),
                                rs.getTimestamp("completed_at").getTime()
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading leaderboard: " + e.getMessage());
            }
            return list;
        });
    }

    public static class CompletionRecord {
        public final String completionId;
        public final String dungeonId;
        public final String difficulty;
        public final int teamSize;
        public final int completionTimeSeconds;
        public final long completedAt;

        public CompletionRecord(String completionId, String dungeonId, String difficulty, int teamSize, int completionTimeSeconds, long completedAt) {
            this.completionId = completionId;
            this.dungeonId = dungeonId;
            this.difficulty = difficulty;
            this.teamSize = teamSize;
            this.completionTimeSeconds = completionTimeSeconds;
            this.completedAt = completedAt;
        }
    }
}
