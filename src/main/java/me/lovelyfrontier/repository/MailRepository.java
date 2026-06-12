package me.lovelyfrontier.repository;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.lock.RetryableTransaction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MailRepository {

    private final LovelyFrontierPlugin plugin;
    private final DatabaseManager databaseManager;

    public MailRepository(LovelyFrontierPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public static class DbMail {
        public final String mailId;
        public final UUID playerUuid;
        public final String dungeonId;
        public final double money;
        public final ItemStack[] items;

        public DbMail(String mailId, UUID playerUuid, String dungeonId, double money, ItemStack[] items) {
            this.mailId = mailId;
            this.playerUuid = playerUuid;
            this.dungeonId = dungeonId;
            this.money = money;
            this.items = items;
        }
    }

    /**
     * Queues mail for a player.
     */
    public CompletableFuture<Boolean> queueMail(UUID playerUuid, String dungeonId, double money, ItemStack[] items) {
        return CompletableFuture.supplyAsync(() -> {
            String mailId = UUID.randomUUID().toString();
            String json = serializeMailData(money, items);

            String sql = "INSERT INTO lf_mail (mail_id, player_uuid, dungeon_id, items_json, claimed, sent_at) VALUES (?, ?, ?, ?, FALSE, CURRENT_TIMESTAMP)";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, mailId);
                ps.setString(2, playerUuid.toString());
                ps.setString(3, dungeonId);
                ps.setString(4, json);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error queueing mail in DB: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Retrieves all unclaimed mail for a player.
     */
    public CompletableFuture<List<DbMail>> getUnclaimedMail(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<DbMail> list = new ArrayList<>();
            String sql = "SELECT mail_id, dungeon_id, items_json FROM lf_mail WHERE player_uuid = ? AND claimed = FALSE";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String mailId = rs.getString("mail_id");
                        String dungeonId = rs.getString("dungeon_id");
                        String json = rs.getString("items_json");

                        double money = deserializeMoney(json);
                        ItemStack[] items = deserializeItems(json);
                        list.add(new DbMail(mailId, playerUuid, dungeonId, money, items));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching unclaimed mail: " + e.getMessage());
            }
            return list;
        });
    }

    /**
     * Marks a mail entry as claimed.
     */
    public CompletableFuture<Boolean> markClaimed(String mailId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE lf_mail SET claimed = TRUE WHERE mail_id = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, mailId);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error marking mail as claimed: " + e.getMessage());
                return false;
            }
        });
    }

    // Helper methods for serialization/deserialization
    private String serializeMailData(double money, ItemStack[] items) {
        try {
            String itemsBase64 = "";
            if (items != null && items.length > 0) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                    dataOutput.writeInt(items.length);
                    for (ItemStack item : items) {
                        dataOutput.writeObject(item);
                    }
                }
                itemsBase64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            }
            return "{\"money\":" + money + ",\"items\":\"" + itemsBase64 + "\"}";
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to serialize mail items: " + e.getMessage());
            return "{\"money\":" + money + ",\"items\":\"\"}";
        }
    }

    private double deserializeMoney(String json) {
        if (json == null || json.isEmpty()) return 0.0;
        try {
            // Quick regex parse to avoid heavyweight JSON libraries
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"money\":\\s*([0-9.]+)");
            java.util.regex.Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error parsing money from mail JSON: " + e.getMessage());
        }
        return 0.0;
    }

    private ItemStack[] deserializeItems(String json) {
        if (json == null || json.isEmpty()) return new ItemStack[0];
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"items\":\\s*\"([^\"]*)\"");
            java.util.regex.Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                String base64 = matcher.group(1);
                if (base64.isEmpty()) return new ItemStack[0];

                byte[] bytes = Base64.getDecoder().decode(base64);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                    int length = dataInput.readInt();
                    ItemStack[] items = new ItemStack[length];
                    for (int i = 0; i < length; i++) {
                        items[i] = (ItemStack) dataInput.readObject();
                    }
                    return items;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to deserialize mail items: " + e.getMessage());
        }
        return new ItemStack[0];
    }
}
