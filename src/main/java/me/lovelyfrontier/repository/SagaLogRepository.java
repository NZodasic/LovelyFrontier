package me.lovelyfrontier.repository;

import me.lovelyfrontier.LovelyFrontierPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;

public class SagaLogRepository {

    private final LovelyFrontierPlugin plugin;
    private final DatabaseManager databaseManager;

    public SagaLogRepository(LovelyFrontierPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Logs a step of the saga to the audit log in the database.
     */
    public CompletableFuture<Boolean> logStep(String sagaId, String instanceId, String stepName, String status, String errorMessage) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO lf_saga_log (saga_id, instance_id, step_name, status, error_message, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, sagaId);
                ps.setString(2, instanceId);
                ps.setString(3, stepName);
                ps.setString(4, status);
                ps.setString(5, errorMessage);
                ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error writing to saga log: " + e.getMessage());
                return false;
            }
        });
    }
}
