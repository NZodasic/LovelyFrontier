package me.lovelyfrontier.manager;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.DungeonInstance;
import me.lovelyfrontier.model.LootPool;
import me.lovelyfrontier.repository.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class LootManager {

    private final LovelyFrontierPlugin plugin;
    private final Map<String, LootPool> lootPools = new ConcurrentHashMap<>();

    public LootManager(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
        loadLootPools();
    }

    /**
     * Loads loot pools from loot_pools.yml.
     */
    public void loadLootPools() {
        File file = new File(plugin.getDataFolder(), "loot_pools.yml");
        if (!file.exists()) {
            plugin.saveResource("loot_pools.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        lootPools.clear();

        if (config.getConfigurationSection("loot_pools") == null) {
            return;
        }

        for (String poolId : config.getConfigurationSection("loot_pools").getKeys(false)) {
            String path = "loot_pools." + poolId + ".";
            double fillRate = config.getDouble(path + "fill_rate", 0.5);
            LootPool pool = new LootPool(poolId, fillRate);

            List<?> itemsList = config.getList(path + "items");
            if (itemsList != null) {
                for (Object itemObj : itemsList) {
                    if (itemObj instanceof Map) {
                        Map<String, Object> itemMap = (Map<String, Object>) itemObj;
                        String matStr = (String) itemMap.get("material");
                        Material mat = Material.matchMaterial(matStr != null ? matStr : "");
                        int weight = ((Number) itemMap.getOrDefault("weight", 10)).intValue();
                        int min = ((Number) itemMap.getOrDefault("min", 1)).intValue();
                        int max = ((Number) itemMap.getOrDefault("max", 1)).intValue();

                        if (mat != null) {
                            ItemStack stack = new ItemStack(mat);
                            LootPool.LootItem lootItem = new LootPool.LootItem(stack, weight, min, max);
                            pool.addItem(lootItem);
                        }
                    }
                }
            }
            lootPools.put(poolId, pool);
        }
        plugin.getLogger().info("Loaded " + lootPools.size() + " loot pools.");
    }

    public LootPool getLootPool(String poolId) {
        return lootPools.get(poolId);
    }

    /**
     * Duplicates the chest definitions from TEMPLATE to the active instance.
     */
    public CompletableFuture<Boolean> cloneChestsToInstance(String dungeonId, String instanceId, String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                // For each template chest, we need a unique chest_id
                // To keep it simple, if we copy them, we can select template chests first, then insert new ones
                String selectSql = "SELECT x, y, z, loot_pool_id FROM lf_dungeon_chests WHERE dungeon_id = ? AND instance_id = 'TEMPLATE'";
                List<TempChest> temps = new ArrayList<>();
                try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                    selectPs.setString(1, dungeonId);
                    try (ResultSet rs = selectPs.executeQuery()) {
                        while (rs.next()) {
                            temps.add(new TempChest(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getString("loot_pool_id")));
                        }
                    }
                }

                String insertSql = "INSERT INTO lf_dungeon_chests (chest_id, dungeon_id, instance_id, world_name, x, y, z, loot_pool_id, opened) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE)";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    for (TempChest tc : temps) {
                        insertPs.setString(1, UUID.randomUUID().toString());
                        insertPs.setString(2, dungeonId);
                        insertPs.setString(3, instanceId);
                        insertPs.setString(4, worldName);
                        insertPs.setInt(5, tc.x);
                        insertPs.setInt(6, tc.y);
                        insertPs.setInt(7, tc.z);
                        insertPs.setString(8, tc.lootPoolId);
                        insertPs.addBatch();
                    }
                    insertPs.executeBatch();
                }
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error cloning chests for instance: " + instanceId, e);
                return false;
            }
        });
    }

    /**
     * Asynchronously loads instance chest positions from DB, shuffles loot per-chest,
     * and schedules inventory updates on the main thread.
     */
    public CompletableFuture<Boolean> populateInstanceChests(String instanceId) {
        DungeonInstance instance = plugin.getInstanceManager().getInstance(instanceId);
        if (instance == null) {
            return CompletableFuture.completedFuture(false);
        }

        String difficulty = instance.getDifficulty();
        int partySize = instance.getMembers().size();
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            String sql = "SELECT x, y, z, loot_pool_id FROM lf_dungeon_chests WHERE instance_id = ? AND opened = FALSE";
            List<PopulateTask> tasks = new ArrayList<>();

            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, instanceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int x = rs.getInt("x");
                        int y = rs.getInt("y");
                        int z = rs.getInt("z");
                        String poolId = rs.getString("loot_pool_id");
                        
                        LootPool pool = lootPools.get(poolId);
                        if (pool != null) {
                            List<ItemStack> items = generateLoot(pool, difficulty, partySize);
                            tasks.add(new PopulateTask(x, y, z, items));
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error reading chest positions for instance " + instanceId, e);
                result.complete(false);
                return;
            }

            // Schedule chest inventory updates on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    World world = Bukkit.getWorld(instance.getWorldName());
                    if (world == null) {
                        result.complete(false);
                        return;
                    }

                    for (PopulateTask task : tasks) {
                        Location loc = new Location(world, task.x, task.y, task.z);
                        BlockState state = loc.getBlock().getState();
                        if (state instanceof Chest) {
                            Chest chest = (Chest) state;
                            Inventory inv = chest.getInventory();
                            inv.clear();

                            Random random = new Random();
                            for (ItemStack item : task.items) {
                                // Place item in a random slot in the chest (typically 27 slots)
                                int slot = random.nextInt(inv.getSize());
                                // If slot is occupied, find first empty slot
                                if (inv.getItem(slot) != null) {
                                    slot = inv.firstEmpty();
                                }
                                if (slot != -1) {
                                    inv.setItem(slot, item);
                                }
                            }
                            chest.update();
                        }
                    }

                    result.complete(true);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "Error filling chests for instance " + instanceId, t);
                    result.complete(false);
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Error preparing chest loot for instance " + instanceId, ex);
            result.complete(false);
            return null;
        });

        return result;
    }

    /**
     * Generates a random set of loot from the pool.
     */
    private List<ItemStack> generateLoot(LootPool pool, String difficulty, int partySize) {
        List<ItemStack> result = new ArrayList<>();
        if (pool.getItems().isEmpty()) {
            return result;
        }

        // 1. Build weighted item list
        List<LootPool.LootItem> flatList = new ArrayList<>();
        for (LootPool.LootItem item : pool.getItems()) {
            for (int i = 0; i < item.getWeight(); i++) {
                flatList.add(item);
            }
        }

        // 2. Fisher-Yates shuffle
        Collections.shuffle(flatList);

        // 3. Pick N items (N = fillRate * 27 slots)
        int chestSlots = 27;
        int itemsToPick = (int) Math.ceil(chestSlots * pool.getFillRate());
        itemsToPick = Math.min(itemsToPick, flatList.size());

        double diffMultiplier = getDifficultyMultiplier(difficulty);
        double partyMultiplier = getPartyMultiplier(partySize);

        Random rand = new Random();
        for (int i = 0; i < itemsToPick; i++) {
            LootPool.LootItem item = flatList.get(i);
            
            // 4. Calculate amount
            int min = item.getMinAmount();
            int max = item.getMaxAmount();
            int baseAmount = min + (min == max ? 0 : rand.nextInt(max - min + 1));
            
            // Amount = baseAmount * difficultyMultiplier * partyMultiplier (total scaling)
            int finalAmount = (int) Math.round(baseAmount * diffMultiplier * partyMultiplier);
            finalAmount = Math.max(1, finalAmount); // Minimum 1

            ItemStack stack = item.getItem().clone();
            stack.setAmount(finalAmount);
            result.add(stack);
        }

        return result;
    }

    private double getDifficultyMultiplier(String difficulty) {
        switch (difficulty.toUpperCase()) {
            case "VERY_EASY": return 0.8;
            case "EASY": return 1.0;
            case "NORMAL": return 1.3;
            case "HARD": return 1.7;
            case "VERY_HARD": return 2.5;
            default: return 1.0;
        }
    }

    private double getPartyMultiplier(String difficulty) {
        // Not used, using size
        return 1.0;
    }

    private double getPartyMultiplier(int size) {
        switch (size) {
            case 1: return 0.70;
            case 2: return 1.00;
            case 3: return 1.10;
            default: return 1.10; // Cap at trio scaling
        }
    }

    /**
     * Persists a tagged chest in the database.
     */
    public CompletableFuture<Boolean> tagChest(String dungeonId, String worldName, int x, int y, int z, String lootPoolId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String updateSql = "UPDATE lf_dungeon_chests SET loot_pool_id = ?, opened = FALSE " +
                        "WHERE dungeon_id = ? AND instance_id = 'TEMPLATE' AND world_name = ? AND x = ? AND y = ? AND z = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, lootPoolId);
                    ps.setString(2, dungeonId);
                    ps.setString(3, worldName);
                    ps.setInt(4, x);
                    ps.setInt(5, y);
                    ps.setInt(6, z);
                    if (ps.executeUpdate() > 0) {
                        return true;
                    }
                }

                String insertSql = "INSERT INTO lf_dungeon_chests (chest_id, dungeon_id, instance_id, world_name, x, y, z, loot_pool_id, opened) " +
                        "VALUES (?, ?, 'TEMPLATE', ?, ?, ?, ?, ?, FALSE)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, dungeonId);
                    ps.setString(3, worldName);
                    ps.setInt(4, x);
                    ps.setInt(5, y);
                    ps.setInt(6, z);
                    ps.setString(7, lootPoolId);
                    ps.executeUpdate();
                }
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error tagging chest", e);
                return false;
            }
        });
    }

    private static class TempChest {
        final int x, y, z;
        final String lootPoolId;

        TempChest(int x, int y, int z, String lootPoolId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lootPoolId = lootPoolId;
        }
    }

    private static class PopulateTask {
        final int x, y, z;
        final List<ItemStack> items;

        PopulateTask(int x, int y, int z, List<ItemStack> items) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.items = items;
        }
    }
}
