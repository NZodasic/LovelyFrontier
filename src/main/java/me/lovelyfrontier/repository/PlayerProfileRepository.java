package me.lovelyfrontier.repository;

import me.lovelyfrontier.LovelyFrontierPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerProfileRepository {

    private final LovelyFrontierPlugin plugin;
    private final DatabaseManager databaseManager;

    public PlayerProfileRepository(LovelyFrontierPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Gets a player's total playtime in hours.
     */
    public CompletableFuture<Double> getPlaytime(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT total_playtime_h FROM lf_player_profile WHERE player_uuid = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("total_playtime_h");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading player playtime: " + e.getMessage());
            }
            return 0.0;
        });
    }

    /**
     * Increments player playtime by a given amount of hours.
     */
    public CompletableFuture<Boolean> incrementPlaytime(UUID uuid, double hours) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO lf_player_profile (player_uuid, total_playtime_h, ip_hash, flagged) " +
                    "VALUES (?, ?, '', FALSE) " +
                    "ON DUPLICATE KEY UPDATE total_playtime_h = total_playtime_h + ?";
            if (!databaseManager.isMySQL()) {
                sql = "INSERT OR REPLACE INTO lf_player_profile (player_uuid, total_playtime_h, ip_hash, flagged) " +
                        "VALUES (?, COALESCE((SELECT total_playtime_h FROM lf_player_profile WHERE player_uuid = ?), 0.0) + ?, '', FALSE)";
            }

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                if (databaseManager.isMySQL()) {
                    ps.setString(1, uuid.toString());
                    ps.setDouble(2, hours);
                    ps.setDouble(3, hours);
                } else {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, uuid.toString());
                    ps.setDouble(3, hours);
                }
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error incrementing player playtime: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Registers or updates a player's IP hash and returns the count of accounts sharing it.
     */
    public CompletableFuture<Integer> registerPlayerIp(UUID uuid, String ipHash) {
        return CompletableFuture.supplyAsync(() -> {
            String insertSql = "INSERT INTO lf_player_profile (player_uuid, total_playtime_h, ip_hash, flagged) " +
                    "VALUES (?, 0.0, ?, FALSE) " +
                    "ON DUPLICATE KEY UPDATE ip_hash = ?";
            if (!databaseManager.isMySQL()) {
                insertSql = "INSERT OR REPLACE INTO lf_player_profile (player_uuid, " +
                        "total_playtime_h, ip_hash, flagged) VALUES (?, " +
                        "COALESCE((SELECT total_playtime_h FROM lf_player_profile WHERE player_uuid = ?), 0.0), ?, " +
                        "COALESCE((SELECT flagged FROM lf_player_profile WHERE player_uuid = ?), FALSE))";
            }

            try (Connection conn = databaseManager.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    if (databaseManager.isMySQL()) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, ipHash);
                        ps.setString(3, ipHash);
                    } else {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, uuid.toString());
                        ps.setString(3, ipHash);
                        ps.setString(4, uuid.toString());
                    }
                    ps.executeUpdate();
                }

                // Count accounts sharing this IP hash
                String countSql = "SELECT COUNT(*) FROM lf_player_profile WHERE ip_hash = ?";
                try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                    ps.setString(1, ipHash);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error checking IP sharing: " + e.getMessage());
            }
            return 1;
        });
    }

    /**
     * Checks if a player is flagged as an alt or for abuse.
     */
    public CompletableFuture<Boolean> isFlagged(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT flagged FROM lf_player_profile WHERE player_uuid = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("flagged");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading player flagged state: " + e.getMessage());
            }
            return false;
        });
    }

    /**
     * Flags or unflags a player.
     */
    public CompletableFuture<Boolean> setFlagged(UUID uuid, boolean flagged) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE lf_player_profile SET flagged = ? WHERE player_uuid = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, flagged);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error setting flagged status: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Gets the timestamp of when the player last claimed a free ticket.
     */
    public CompletableFuture<java.sql.Timestamp> getLastFreeTicketAt(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT last_free_ticket_at FROM lf_player_profile WHERE player_uuid = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getTimestamp("last_free_ticket_at");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading player last_free_ticket_at: " + e.getMessage());
            }
            return null;
        });
    }

    /**
     * Updates the timestamp of when the player last claimed a free ticket.
     */
    public CompletableFuture<Boolean> updateLastFreeTicketAt(UUID uuid, java.sql.Timestamp time) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE lf_player_profile SET last_free_ticket_at = ? WHERE player_uuid = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setTimestamp(1, time);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error setting last_free_ticket_at status: " + e.getMessage());
                return false;
            }
        });
    }
}
