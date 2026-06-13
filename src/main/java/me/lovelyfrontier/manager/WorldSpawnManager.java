package me.lovelyfrontier.manager;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.DungeonConfig;
import me.lovelyfrontier.repository.PortalRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class WorldSpawnManager {

    private final LovelyFrontierPlugin plugin;
    private final Map<String, PortalRepository.DbPortal> activeWorldPortals = new ConcurrentHashMap<>();
    private BukkitTask spawnTask;
    private BukkitTask expiryTask;

    public WorldSpawnManager(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the schedulers for world portal spawning and expiration checking.
     */
    public void start() {
        if (!plugin.getConfigManager().isWorldDungeonSpawnEnabled()) {
            plugin.getLogger().info("World Dungeon Spawning is disabled in config.yml.");
            return;
        }

        plugin.getLogger().info("Starting World Dungeon Spawning systems...");

        // Load existing active portals from DB on startup
        plugin.getPortalRepository().getActivePortals().thenAccept(portals -> {
            for (PortalRepository.DbPortal portal : portals) {
                activeWorldPortals.put(portal.portalId, portal);
            }
            plugin.getLogger().info("Loaded " + activeWorldPortals.size() + " active world portals from database.");
        });

        // 1. Spawning Scheduler
        int checkInterval = plugin.getConfigManager().getWorldDungeonSpawnCheckInterval();
        spawnTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                // Only spawn if we haven't reached the active instance limit
                if (activeWorldPortals.size() < 10) { // Limit to 10 active portals at a time
                    spawnRandomPortal();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error in WorldSpawnManager spawning task", e);
            }
        }, 100L, checkInterval * 20L); // Check interval converted to ticks

        // 2. Expiration Checker (Every 10 seconds)
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                checkExpiredPortals();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error in WorldSpawnManager expiration task", e);
            }
        }, 200L, 200L); // Every 10 seconds (200 ticks)
    }

    /**
     * Stops all active tasks and schedules cleanup.
     */
    public void stop() {
        if (spawnTask != null) {
            spawnTask.cancel();
        }
        if (expiryTask != null) {
            expiryTask.cancel();
        }
    }

    /**
     * Attempts to find a safe location and spawn a random portal.
     */
    private void spawnRandomPortal() {
        Collection<DungeonConfig> dungeonsColl = plugin.getDungeonManager().getAllDungeons();
        if (dungeonsColl.isEmpty()) return;
        List<DungeonConfig> dungeons = new ArrayList<>(dungeonsColl);

        // Pick random dungeon
        DungeonConfig dungeon = dungeons.get(new Random().nextInt(dungeons.size()));

        // Resolve world - Default is first world loaded
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorlds().get(0);
            if (world == null) return;
            findSafeLocationAsync(world, 0, dungeon);
        });
    }

    /**
     * Finds a safe ground location asynchronously.
     */
    private void findSafeLocationAsync(World world, int attempt, DungeonConfig dungeon) {
        if (attempt >= 30) {
            plugin.getLogger().warning("Could not find a safe location to spawn a world portal for " + dungeon.getId() + " after 30 attempts.");
            return;
        }

        Random rand = new Random();
        Location spawnLoc = world.getSpawnLocation();
        int minX = spawnLoc.getBlockX() - 2000;
        int maxX = spawnLoc.getBlockX() + 2000;
        int minZ = spawnLoc.getBlockZ() - 2000;
        int maxZ = spawnLoc.getBlockZ() + 2000;

        int x = rand.nextInt(maxX - minX) + minX;
        int z = rand.nextInt(maxZ - minZ) + minZ;

        // Load chunk asynchronously
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int y = world.getHighestBlockYAt(x, z);

                if (y <= world.getMinHeight()) {
                    findSafeLocationAsync(world, attempt + 1, dungeon);
                    return;
                }

                Block topBlock = world.getBlockAt(x, y, z);
                Material topType = topBlock.getType();

                // Skip if the highest block is leaves, logs, wood, water, lava, or air (not in the open / on a tree)
                if (topType.name().contains("LEAVES") ||
                    topType.name().contains("LOG") ||
                    topType.name().contains("WOOD") ||
                    topType == Material.WATER ||
                    topType == Material.LAVA ||
                    topType == Material.AIR) {
                    findSafeLocationAsync(world, attempt + 1, dungeon);
                    return;
                }

                Block groundBlock = world.getBlockAt(x, y - 1, z);
                Material groundType = groundBlock.getType();

                // Ground must be solid and not liquid/leaves/wood/air
                if (groundType.isSolid() &&
                    groundType != Material.WATER &&
                    groundType != Material.LAVA &&
                    groundType != Material.AIR &&
                    !groundType.name().contains("LEAVES") &&
                    !groundType.name().contains("LOG") &&
                    !groundType.name().contains("WOOD")) {

                    // Spawn portal
                    spawnPortalAtLocation(world, x, y, z, dungeon);
                } else {
                    findSafeLocationAsync(world, attempt + 1, dungeon);
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Error loading chunk asynchronously for portal spawn", ex);
            return null;
        });
    }

    /**
     * Spawns the portal blocks at the given location (must be called on the main thread).
     */
    public void spawnPortalAtLocation(World world, int cx, int cy, int cz, DungeonConfig dungeon) {
        // Build Beacon structure (cy is iron base block level, cy+1 is beacon, cy+2 is glass)
        // Base layer: 3x3 of Iron Blocks
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(cx + dx, cy, cz + dz).setType(Material.IRON_BLOCK);
            }
        }

        // Beacon layer
        world.getBlockAt(cx, cy + 1, cz).setType(Material.BEACON);

        // Glass layer - colored glass based on dungeon difficulty/hash
        Material glassColor = getGlassColorForDungeon(dungeon.getId());
        world.getBlockAt(cx, cy + 2, cz).setType(glassColor);

        // Generate portal details
        String portalId = "world_portal_" + UUID.randomUUID().toString().substring(0, 8);
        int lifetime = plugin.getConfigManager().getWorldDungeonSpawnPortalLifetime();
        long expiresAt = System.currentTimeMillis() + (lifetime * 1000L);

        // Save to DB and map
        plugin.getPortalRepository().savePortal(portalId, dungeon.getId(), world.getName(), cx, cy, cz, expiresAt)
            .thenAccept(saved -> {
                if (saved) {
                    PortalRepository.DbPortal portal = new PortalRepository.DbPortal(
                            portalId, dungeon.getId(), world.getName(), cx, cy, cz, null, expiresAt
                    );
                    activeWorldPortals.put(portalId, portal);

                    // Broadcast notifications
                    Bukkit.getScheduler().runTask(plugin, () -> notifyPlayersOfSpawn(portal, dungeon.getName()));
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> removePortalBlocks(world, cx, cy, cz));
                }
            });
    }

    /**
     * Selects a specific stained glass color based on the dungeon ID.
     */
    private Material getGlassColorForDungeon(String dungeonId) {
        int hash = Math.abs(dungeonId.hashCode());
        Material[] colors = {
                Material.PURPLE_STAINED_GLASS,
                Material.RED_STAINED_GLASS,
                Material.BLUE_STAINED_GLASS,
                Material.GREEN_STAINED_GLASS,
                Material.ORANGE_STAINED_GLASS,
                Material.MAGENTA_STAINED_GLASS,
                Material.CYAN_STAINED_GLASS
        };
        return colors[hash % colors.length];
    }

    /**
     * Sends spawn notifications based on configuration.
     */
    private void notifyPlayersOfSpawn(PortalRepository.DbPortal portal, String dungeonName) {
        String mode = plugin.getConfigManager().getWorldDungeonSpawnNotificationMode().toUpperCase();
        String message = "§a[LovelyFrontier] Một Cổng Phụ Bản §e" + dungeonName + 
                " §avừa xuất hiện tại §f" + (int)portal.x + ", " + (int)portal.y + ", " + (int)portal.z + 
                " §atrong thế giới §f" + portal.worldName + "§a! Cổng sẽ biến mất sau " + 
                (plugin.getConfigManager().getWorldDungeonSpawnPortalLifetime() / 60) + " phút.";

        if (mode.equals("GLOBAL") || mode.equals("BOTH")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
            }
        } else if (mode.equals("LOCAL")) {
            // Send to players within 300 blocks
            World world = Bukkit.getWorld(portal.worldName);
            if (world != null) {
                Location portalLoc = new Location(world, portal.x, portal.y, portal.z);
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distanceSquared(portalLoc) <= 90000.0) { // 300^2
                        player.sendMessage(message);
                    }
                }
            }
        }
    }

    /**
     * Sends expiration notifications based on configuration.
     */
    private void notifyPlayersOfExpiration(PortalRepository.DbPortal portal, String dungeonName) {
        String mode = plugin.getConfigManager().getWorldDungeonSpawnNotificationMode().toUpperCase();
        String message = "§c[LovelyFrontier] Cổng Phụ Bản §e" + dungeonName + 
                " §ctại §f" + (int)portal.x + ", " + (int)portal.y + ", " + (int)portal.z + 
                " §ctrong thế giới §f" + portal.worldName + " §cđã hết hạn và biến mất!";

        if (mode.equals("GLOBAL") || mode.equals("BOTH")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
            }
        } else if (mode.equals("LOCAL")) {
            // Send to players within 300 blocks
            World world = Bukkit.getWorld(portal.worldName);
            if (world != null) {
                Location portalLoc = new Location(world, portal.x, portal.y, portal.z);
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distanceSquared(portalLoc) <= 90000.0) { // 300^2
                        player.sendMessage(message);
                    }
                }
            }
        }
    }

    /**
     * Checks and despawns expired portals from DB and memory.
     */
    private void checkExpiredPortals() {
        plugin.getPortalRepository().getExpiredPortals().thenAccept(expired -> {
            for (PortalRepository.DbPortal portal : expired) {
                despawnPortal(portal.portalId, true);
            }
        });
    }

    /**
     * Despawns a portal by removing its blocks, deleting from DB and memory.
     */
    public void despawnPortal(String portalId, boolean wasExpired) {
        PortalRepository.DbPortal portal = activeWorldPortals.remove(portalId);
        if (portal == null) return;

        // Perform block removal on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(portal.worldName);
            if (world == null) return;

            int cx = (int) portal.x;
            int cy = (int) portal.y;
            int cz = (int) portal.z;

            removePortalBlocks(world, cx, cy, cz);

            // Delete from database
            plugin.getPortalRepository().deletePortal(portalId).thenAccept(deleted -> {
                if (deleted && wasExpired) {
                    plugin.getLogger().info("World portal " + portalId + " has expired and was despawned.");
                    DungeonConfig dungeon = plugin.getDungeonManager().getDungeon(portal.dungeonId);
                    String dungeonName = dungeon != null ? dungeon.getName() : portal.dungeonId;
                    Bukkit.getScheduler().runTask(plugin, () -> notifyPlayersOfExpiration(portal, dungeonName));
                }
            });
        });
    }

    private void removePortalBlocks(World world, int cx, int cy, int cz) {
        world.getBlockAt(cx, cy + 2, cz).setType(Material.AIR);
        world.getBlockAt(cx, cy + 1, cz).setType(Material.AIR);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(cx + dx, cy, cz + dz).setType(Material.AIR);
            }
        }
    }

    /**
     * Force spawns a world portal for the specified dungeon at a random safe location.
     */
    public void forceSpawnRandom(DungeonConfig dungeon, World world) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            findSafeLocationAsync(world, 0, dungeon);
        });
    }

    /**
     * Force spawns a world portal for the specified dungeon at the exact location.
     */
    public void forceSpawnAt(DungeonConfig dungeon, Location loc) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            spawnPortalAtLocation(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), dungeon);
        });
    }

    public Map<String, PortalRepository.DbPortal> getActiveWorldPortals() {
        return activeWorldPortals;
    }
}
