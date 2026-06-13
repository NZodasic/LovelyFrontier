package me.lovelyfrontier.repository;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.lock.RetryableTransaction;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TicketRepository {

    private final LovelyFrontierPlugin plugin;
    private final DatabaseManager databaseManager;

    public TicketRepository(LovelyFrontierPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public CompletableFuture<String> findValidTicket(UUID ownerUuid, String dungeonId, String difficulty) {
        return CompletableFuture.supplyAsync(() -> {
            String targetDungeon = plugin.getConfigManager().isTicketsUniversalTicket() ? "UNIVERSAL" : dungeonId;
            String targetDifficulty = plugin.getConfigManager().isTicketsUniversalTicket() ? "UNIVERSAL" : difficulty;
            String sql = "SELECT ticket_id FROM lf_tickets WHERE owner_uuid = ? AND dungeon_id = ? AND difficulty = ? AND quantity > 0";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                ps.setString(2, targetDungeon);
                ps.setString(3, targetDifficulty);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("ticket_id");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error finding valid ticket: " + e.getMessage());
            }
            return null;
        });
    }

    /**
     * Consumes one ticket. Decrements quantity, deletes row if 0.
     */
    public CompletableFuture<Boolean> consumeTicket(String ticketId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                return RetryableTransaction.execute(conn, () -> {
                    // Lock tickets table for update if MySQL
                    String selectSql = databaseManager.isMySQL() 
                        ? "SELECT quantity FROM lf_tickets WHERE ticket_id = ? FOR UPDATE"
                        : "SELECT quantity FROM lf_tickets WHERE ticket_id = ?";
                    
                    int qty = 0;
                    try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                        ps.setString(1, ticketId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                qty = rs.getInt("quantity");
                            }
                        }
                    }

                    if (qty <= 0) {
                        return false;
                    }

                    if (qty == 1) {
                        String deleteSql = "DELETE FROM lf_tickets WHERE ticket_id = ?";
                        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                            ps.setString(1, ticketId);
                            ps.executeUpdate();
                        }
                    } else {
                        String updateSql = "UPDATE lf_tickets SET quantity = quantity - 1 WHERE ticket_id = ?";
                        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                            ps.setString(1, ticketId);
                            ps.executeUpdate();
                        }
                    }
                    return true;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Error consuming ticket: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Refunds a consumed ticket. Increments quantity or inserts a new row if deleted.
     */
    public CompletableFuture<Boolean> refundTicket(String ticketId, UUID ownerUuid, String dungeonId, String difficulty) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                return RetryableTransaction.execute(conn, () -> {
                    // Check if ticket still exists
                    String selectSql = databaseManager.isMySQL()
                        ? "SELECT quantity FROM lf_tickets WHERE ticket_id = ? FOR UPDATE"
                        : "SELECT quantity FROM lf_tickets WHERE ticket_id = ?";
                    
                    boolean exists = false;
                    try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                        ps.setString(1, ticketId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                exists = true;
                            }
                        }
                    }

                    if (exists) {
                        String updateSql = "UPDATE lf_tickets SET quantity = quantity + 1 WHERE ticket_id = ?";
                        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                            ps.setString(1, ticketId);
                            ps.executeUpdate();
                        }
                    } else {
                        // Re-create the ticket
                        String targetDungeon = plugin.getConfigManager().isTicketsUniversalTicket() ? "UNIVERSAL" : dungeonId;
                        String targetDifficulty = plugin.getConfigManager().isTicketsUniversalTicket() ? "UNIVERSAL" : difficulty;
                        if (!databaseManager.isMySQL()) {
                            String insertSql = "INSERT OR IGNORE INTO lf_tickets (ticket_id, owner_uuid, dungeon_id, difficulty, quantity) VALUES (?, ?, ?, ?, 0)";
                            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                                ps.setString(1, ticketId);
                                ps.setString(2, ownerUuid.toString());
                                ps.setString(3, targetDungeon);
                                ps.setString(4, targetDifficulty);
                                ps.executeUpdate();
                            }
                            String updateSql = "UPDATE lf_tickets SET quantity = quantity + 1 WHERE owner_uuid = ? AND dungeon_id = ? AND difficulty = ?";
                            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                                ps.setString(1, ownerUuid.toString());
                                ps.setString(2, targetDungeon);
                                ps.setString(3, targetDifficulty);
                                ps.executeUpdate();
                            }
                        } else {
                            String insertSql = "INSERT INTO lf_tickets (ticket_id, owner_uuid, dungeon_id, difficulty, quantity) " +
                                "VALUES (?, ?, ?, ?, 1) ON DUPLICATE KEY UPDATE quantity = quantity + 1";
                            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                                ps.setString(1, ticketId);
                                ps.setString(2, ownerUuid.toString());
                                ps.setString(3, targetDungeon);
                                ps.setString(4, targetDifficulty);
                                ps.executeUpdate();
                            }
                        }
                    }
                    return true;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Error refunding ticket: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Adds tickets to a player. Inserts or updates.
     */
    public CompletableFuture<Boolean> addTicket(UUID ownerUuid, String dungeonId, String difficulty, int quantity) {
        return CompletableFuture.supplyAsync(() -> {
            String targetDungeon = plugin.getConfigManager().isTicketsUniversalTicket() ? "UNIVERSAL" : dungeonId;
            String targetDifficulty = plugin.getConfigManager().isTicketsUniversalTicket() ? "UNIVERSAL" : difficulty;
            try (Connection conn = databaseManager.getConnection()) {
                return RetryableTransaction.execute(conn, () -> {
                    String selectSql = databaseManager.isMySQL()
                        ? "SELECT ticket_id, quantity FROM lf_tickets WHERE owner_uuid = ? AND dungeon_id = ? AND difficulty = ? FOR UPDATE"
                        : "SELECT ticket_id, quantity FROM lf_tickets WHERE owner_uuid = ? AND dungeon_id = ? AND difficulty = ?";
                    
                    String ticketId = null;
                    int currentQty = 0;
                    try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                        ps.setString(1, ownerUuid.toString());
                        ps.setString(2, targetDungeon);
                        ps.setString(3, targetDifficulty);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                ticketId = rs.getString("ticket_id");
                                currentQty = rs.getInt("quantity");
                            }
                        }
                    }

                    if (ticketId != null) {
                        String updateSql = "UPDATE lf_tickets SET quantity = quantity + ? WHERE ticket_id = ?";
                        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                            ps.setInt(1, quantity);
                            ps.setString(2, ticketId);
                            ps.executeUpdate();
                        }
                    } else {
                        String insertSql = "INSERT INTO lf_tickets (ticket_id, owner_uuid, dungeon_id, difficulty, quantity) VALUES (?, ?, ?, ?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                            ps.setString(1, UUID.randomUUID().toString());
                            ps.setString(2, ownerUuid.toString());
                            ps.setString(3, targetDungeon);
                            ps.setString(4, targetDifficulty);
                            ps.setInt(5, quantity);
                            ps.executeUpdate();
                        }
                    }
                    return true;
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding ticket: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Gets the number of tickets a player has for a specific dungeon and difficulty.
     */
    public CompletableFuture<Integer> getTicketCount(UUID ownerUuid, String dungeonId, String difficulty) {
        return CompletableFuture.supplyAsync(() -> {
            String targetDungeon = plugin.getConfigManager().isTicketsUniversalTicket() ? "UNIVERSAL" : dungeonId;
            String targetDifficulty = plugin.getConfigManager().isTicketsUniversalTicket() ? "UNIVERSAL" : difficulty;
            String sql = "SELECT quantity FROM lf_tickets WHERE owner_uuid = ? AND dungeon_id = ? AND difficulty = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                ps.setString(2, targetDungeon);
                ps.setString(3, targetDifficulty);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("quantity");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error getting ticket count: " + e.getMessage());
            }
            return 0;
        });
    }

    /**
     * Gets all tickets for a player.
     * Returns a string representation or custom map if preferred, let's return a map or list.
     */
    public CompletableFuture<java.util.Map<String, Integer>> getAllTickets(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            java.util.Map<String, Integer> map = new java.util.HashMap<>();
            String sql = "SELECT dungeon_id, difficulty, quantity FROM lf_tickets WHERE owner_uuid = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String key = rs.getString("dungeon_id") + ":" + rs.getString("difficulty");
                        map.put(key, rs.getInt("quantity"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error getting all tickets: " + e.getMessage());
            }
            return map;
        });
    }
}
