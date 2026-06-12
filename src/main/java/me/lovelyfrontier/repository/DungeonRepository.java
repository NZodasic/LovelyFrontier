package me.lovelyfrontier.repository;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.lock.RetryableTransaction;
import me.lovelyfrontier.model.DungeonConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DungeonRepository {

    private final LovelyFrontierPlugin plugin;
    private final DatabaseManager databaseManager;

    public DungeonRepository(LovelyFrontierPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Loads all dungeon configurations from the database.
     */
    public CompletableFuture<List<DungeonConfig>> loadAllDungeons() {
        return CompletableFuture.supplyAsync(() -> {
            List<DungeonConfig> list = new ArrayList<>();
            String sql = "SELECT * FROM lf_dungeons";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DungeonConfig config = new DungeonConfig(
                            rs.getString("dungeon_id"),
                            rs.getString("name"),
                            rs.getString("schematic_path"),
                            rs.getInt("min_party_size"),
                            rs.getInt("time_limit_seconds"),
                            rs.getDouble("spawn_x"),
                            rs.getDouble("spawn_y"),
                            rs.getDouble("spawn_z"),
                            rs.getFloat("spawn_yaw"),
                            rs.getFloat("spawn_pitch")
                    );
                    list.add(config);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading dungeons: " + e.getMessage());
            }
            return list;
        });
    }

    /**
     * Saves or updates a dungeon configuration in the database.
     */
    public CompletableFuture<Boolean> saveDungeon(DungeonConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                return RetryableTransaction.execute(conn, () -> {
                    String selectSql = "SELECT dungeon_id FROM lf_dungeons WHERE dungeon_id = ?";
                    boolean exists = false;
                    try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                        ps.setString(1, config.getId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                exists = true;
                            }
                        }
                    }

                    if (exists) {
                        String updateSql = "UPDATE lf_dungeons SET name = ?, schematic_path = ?, min_party_size = ?, " +
                                "time_limit_seconds = ?, spawn_x = ?, spawn_y = ?, spawn_z = ?, spawn_yaw = ?, spawn_pitch = ? " +
                                "WHERE dungeon_id = ?";
                        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                            ps.setString(1, config.getName());
                            ps.setString(2, config.getSchematicPath());
                            ps.setInt(3, config.getMinPartySize());
                            ps.setInt(4, config.getTimeLimitSeconds());
                            ps.setDouble(5, config.getSpawnX());
                            ps.setDouble(6, config.getSpawnY());
                            ps.setDouble(7, config.getSpawnZ());
                            ps.setFloat(8, config.getSpawnYaw());
                            ps.setFloat(9, config.getSpawnPitch());
                            ps.setString(10, config.getId());
                            ps.executeUpdate();
                        }
                    } else {
                        String insertSql = "INSERT INTO lf_dungeons (dungeon_id, name, schematic_path, min_party_size, " +
                                "time_limit_seconds, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                            ps.setString(1, config.getId());
                            ps.setString(2, config.getName());
                            ps.setString(3, config.getSchematicPath());
                            ps.setInt(4, config.getMinPartySize());
                            ps.setInt(5, config.getTimeLimitSeconds());
                            ps.setDouble(6, config.getSpawnX());
                            ps.setDouble(7, config.getSpawnY());
                            ps.setDouble(8, config.getSpawnZ());
                            ps.setFloat(9, config.getSpawnYaw());
                            ps.setFloat(10, config.getSpawnPitch());
                            ps.executeUpdate();
                        }
                    }
                    return true;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving dungeon config in DB: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Deletes a dungeon configuration from the database.
     */
    public CompletableFuture<Boolean> deleteDungeon(String id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM lf_dungeons WHERE dungeon_id = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error deleting dungeon config from DB: " + e.getMessage());
                return false;
            }
        });
    }
}
