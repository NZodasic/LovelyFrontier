package me.lovelyfrontier.repository;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.lock.RetryableTransaction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PortalRepository {

    private final LovelyFrontierPlugin plugin;
    private final DatabaseManager databaseManager;

    public PortalRepository(LovelyFrontierPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Saves a world spawn portal to the database.
     */
    public CompletableFuture<Boolean> savePortal(String portalId, String dungeonId, String worldName,
                                                 double x, double y, double z, long expiresAt) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO lf_world_portals (portal_id, dungeon_id, world_name, x, y, z, locked_by, lock_version, expires_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, NULL, 0, ?)";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, portalId);
                ps.setString(2, dungeonId);
                ps.setString(3, worldName);
                ps.setDouble(4, x);
                ps.setDouble(5, y);
                ps.setDouble(6, z);
                ps.setTimestamp(7, new Timestamp(expiresAt));
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving world portal to DB: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Tries to acquire a persistent database lock on a portal.
     * Uses pessimistic FOR UPDATE lock followed by optimistic version check.
     */
    public CompletableFuture<Boolean> tryAcquireLock(String portalId, UUID leaderUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                return RetryableTransaction.execute(conn, () -> {
                    // Documenting multi-table transaction rule: R-005 lock order: 5. lf_world_portals
                    String selectSql = databaseManager.isMySQL()
                        ? "SELECT locked_by, lock_version FROM lf_world_portals WHERE portal_id = ? FOR UPDATE"
                        : "SELECT locked_by, lock_version FROM lf_world_portals WHERE portal_id = ?";
                    
                    String lockedBy = null;
                    int version = 0;
                    try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                        ps.setString(1, portalId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                lockedBy = rs.getString("locked_by");
                                version = rs.getInt("lock_version");
                            } else {
                                return false; // Portal doesn't exist
                            }
                        }
                    }

                    if (lockedBy != null) {
                        return false; // Already locked
                    }

                    String updateSql = "UPDATE lf_world_portals SET locked_by = ?, lock_version = lock_version + 1 " +
                            "WHERE portal_id = ? AND lock_version = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setString(1, leaderUuid.toString());
                        ps.setString(2, portalId);
                        ps.setInt(3, version);
                        int updated = ps.executeUpdate();
                        return updated > 0;
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Error acquiring portal lock in DB: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Releases a persistent database lock on a portal.
     */
    public CompletableFuture<Boolean> releaseLock(String portalId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE lf_world_portals SET locked_by = NULL WHERE portal_id = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, portalId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error releasing portal lock in DB: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Deletes a portal from the database.
     */
    public CompletableFuture<Boolean> deletePortal(String portalId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM lf_world_portals WHERE portal_id = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, portalId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error deleting portal from DB: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Retrieves all active portals from the database.
     */
    public CompletableFuture<List<DbPortal>> getActivePortals() {
        return CompletableFuture.supplyAsync(() -> {
            List<DbPortal> list = new ArrayList<>();
            String sql = "SELECT * FROM lf_world_portals";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new DbPortal(
                            rs.getString("portal_id"),
                            rs.getString("dungeon_id"),
                            rs.getString("world_name"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getString("locked_by"),
                            rs.getTimestamp("expires_at").getTime()
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading active portals from DB: " + e.getMessage());
            }
            return list;
        });
    }

    /**
     * Retrieves all expired portals.
     */
    public CompletableFuture<List<DbPortal>> getExpiredPortals() {
        return CompletableFuture.supplyAsync(() -> {
            List<DbPortal> list = new ArrayList<>();
            String sql = "SELECT * FROM lf_world_portals WHERE expires_at < ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new DbPortal(
                                rs.getString("portal_id"),
                                rs.getString("dungeon_id"),
                                rs.getString("world_name"),
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                rs.getString("locked_by"),
                                rs.getTimestamp("expires_at").getTime()
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading expired portals from DB: " + e.getMessage());
            }
            return list;
        });
    }

    public static class DbPortal {
        public final String portalId;
        public final String dungeonId;
        public final String worldName;
        public final double x, y, z;
        public final String lockedBy;
        public final long expiresAt;

        public DbPortal(String portalId, String dungeonId, String worldName, double x, double y, double z, String lockedBy, long expiresAt) {
            this.portalId = portalId;
            this.dungeonId = dungeonId;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lockedBy = lockedBy;
            this.expiresAt = expiresAt;
        }
    }
}
