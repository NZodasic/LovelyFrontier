package me.lovelyfrontier.repository;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.lock.RetryableTransaction;
import me.lovelyfrontier.model.DungeonInstance;
import me.lovelyfrontier.model.InstanceState;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class InstanceRepository {

    private final LovelyFrontierPlugin plugin;
    private final DatabaseManager databaseManager;

    public InstanceRepository(LovelyFrontierPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Saves a new DungeonInstance and its members in a single transaction.
     */
    public CompletableFuture<Boolean> createInstance(DungeonInstance instance) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                return RetryableTransaction.execute(conn, () -> {
                    // Lock order: 4. lf_instances
                    String sqlInst = "INSERT INTO lf_instances (instance_id, dungeon_id, difficulty, state, boss_cleared, boss_hp_percent, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sqlInst)) {
                        ps.setString(1, instance.getInstanceId());
                        ps.setString(2, instance.getDungeonId());
                        ps.setString(3, instance.getDifficulty());
                        ps.setString(4, instance.getState().name());
                        ps.setBoolean(5, instance.isBossCleared());
                        ps.setDouble(6, instance.getBossHpPercent());
                        ps.setTimestamp(7, new Timestamp(instance.getCreatedAt()));
                        ps.executeUpdate();
                    }

                    String sqlMem = "INSERT INTO lf_instance_members (instance_id, player_uuid, connected, disconnect_at) VALUES (?, ?, ?, NULL)";
                    try (PreparedStatement ps = conn.prepareStatement(sqlMem)) {
                        for (UUID uuid : instance.getMembers()) {
                            ps.setString(1, instance.getInstanceId());
                            ps.setString(2, uuid.toString());
                            ps.setBoolean(3, true);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    return true;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Error creating instance in DB: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Updates the state of an instance.
     */
    public CompletableFuture<Boolean> updateState(String instanceId, InstanceState state) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE lf_instances SET state = ? WHERE instance_id = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, state.name());
                ps.setString(2, instanceId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error updating instance state in DB: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Updates the boss HP percent of an instance.
     */
    public CompletableFuture<Boolean> updateBossHp(String instanceId, double hpPercent) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE lf_instances SET boss_hp_percent = ? WHERE instance_id = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDouble(1, hpPercent);
                ps.setString(2, instanceId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error updating boss HP in DB: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Marks the boss of an instance as cleared.
     */
    public CompletableFuture<Boolean> markBossCleared(String instanceId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE lf_instances SET boss_cleared = TRUE, boss_hp_percent = 0.0 WHERE instance_id = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, instanceId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error marking boss cleared in DB: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Updates a player's connection state in the instance.
     * Sets disconnect_at to current timestamp if disconnected is true, otherwise sets it to NULL.
     */
    public CompletableFuture<Boolean> updateMemberConnection(String instanceId, UUID playerUuid, boolean connected) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE lf_instance_members SET connected = ?, disconnect_at = ? WHERE instance_id = ? AND player_uuid = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, connected);
                ps.setTimestamp(2, connected ? null : new Timestamp(System.currentTimeMillis()));
                ps.setString(3, instanceId);
                ps.setString(4, playerUuid.toString());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error updating member connection in DB: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Retrieves all members of an instance and their connection status.
     */
    public CompletableFuture<Map<UUID, Boolean>> getMembers(String instanceId) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Boolean> map = new HashMap<>();
            String sql = "SELECT player_uuid, connected FROM lf_instance_members WHERE instance_id = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, instanceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        map.put(UUID.fromString(rs.getString("player_uuid")), rs.getBoolean("connected"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error getting instance members from DB: " + e.getMessage());
            }
            return map;
        });
    }

    /**
     * Cleans up an instance by setting its state to CLEANUP in the DB and removing members.
     */
    public CompletableFuture<Boolean> deleteInstance(String instanceId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                return RetryableTransaction.execute(conn, () -> {
                    // Update state to CLEANUP
                    String updateSql = "UPDATE lf_instances SET state = 'CLEANUP' WHERE instance_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setString(1, instanceId);
                        ps.executeUpdate();
                    }
                    // Delete members
                    String deleteSql = "DELETE FROM lf_instance_members WHERE instance_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                        ps.setString(1, instanceId);
                        ps.executeUpdate();
                    }
                    return true;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Error deleting/cleaning up instance in DB: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Retrieves all active instances (state != CLEANUP) from the DB.
     */
    public CompletableFuture<List<DungeonInstance>> getActiveInstances() {
        return CompletableFuture.supplyAsync(() -> {
            List<DungeonInstance> list = new ArrayList<>();
            String sql = "SELECT * FROM lf_instances WHERE state != 'CLEANUP'";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String instanceId = rs.getString("instance_id");
                    String dungeonId = rs.getString("dungeon_id");
                    String difficulty = rs.getString("difficulty");
                    String stateStr = rs.getString("state");
                    
                    // Construct local instance model
                    String worldName = "lf_instance_" + instanceId;
                    DungeonInstance inst = new DungeonInstance(instanceId, dungeonId, difficulty, worldName);
                    inst.setState(InstanceState.valueOf(stateStr));
                    inst.setBossCleared(rs.getBoolean("boss_cleared"));
                    inst.setBossHpPercent(rs.getDouble("boss_hp_percent"));
                    Timestamp createdAtTs = rs.getTimestamp("created_at");
                    if (createdAtTs != null) {
                        inst.setCreatedAt(createdAtTs.getTime());
                    }
                    list.add(inst);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error getting active instances from DB: " + e.getMessage());
            }
            return list;
        });
    }

    /**
     * Forcefully changes states of all active instances to CLEANUP (used on plugin disable).
     */
    public CompletableFuture<Boolean> markAllActiveAsCleanup() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE lf_instances SET state = 'CLEANUP' WHERE state != 'CLEANUP'";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error setting all active instances to CLEANUP in DB: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Forcefully adds a player as a member to an active instance.
     */
    public CompletableFuture<Boolean> addMember(String instanceId, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql;
                if (databaseManager.isMySQL()) {
                    sql = "INSERT INTO lf_instance_members (instance_id, player_uuid, connected, disconnect_at) VALUES (?, ?, ?, NULL) " +
                          "ON DUPLICATE KEY UPDATE connected = ?, disconnect_at = NULL";
                } else {
                    sql = "INSERT OR REPLACE INTO lf_instance_members (instance_id, player_uuid, connected, disconnect_at) VALUES (?, ?, ?, NULL)";
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, instanceId);
                    ps.setString(2, playerUuid.toString());
                    ps.setBoolean(3, true);
                    if (databaseManager.isMySQL()) {
                        ps.setBoolean(4, true);
                    }
                    ps.executeUpdate();
                    return true;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding instance member to DB: " + e.getMessage());
                return false;
            }
        });
    }
}
