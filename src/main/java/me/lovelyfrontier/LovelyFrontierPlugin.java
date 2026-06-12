package me.lovelyfrontier;

import org.mvplugins.multiverse.core.MultiverseCore;
import me.lovelyfrontier.command.PlayerCommandManager;
import me.lovelyfrontier.command.AdminCommandManager;
import me.lovelyfrontier.lock.InMemoryPortalLock;
import me.lovelyfrontier.manager.*;
import me.lovelyfrontier.model.DungeonInstance;
import me.lovelyfrontier.model.InstanceState;
import me.lovelyfrontier.repository.*;
import me.lovelyfrontier.listener.ChestProtectionListener;
import me.lovelyfrontier.listener.PortalListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class LovelyFrontierPlugin extends JavaPlugin {

    private static LovelyFrontierPlugin instance;

    // Config Manager
    private ConfigManager configManager;

    // Database & Repositories
    private DatabaseManager databaseManager;
    private DungeonRepository dungeonRepository;
    private TicketRepository ticketRepository;
    private SessionRepository sessionRepository;
    private InstanceRepository instanceRepository;
    private PortalRepository portalRepository;
    private SagaLogRepository sagaLogRepo;
    private CompletionRepository completionRepository;
    private PlayerProfileRepository playerProfileRepository;
    private MailRepository mailRepository;

    // Lock Layer
    private InMemoryPortalLock inMemoryPortalLock;

    // Managers
    private DungeonManager dungeonManager;
    private InstanceManager instanceManager;
    private PortalManager portalManager;
    private LootManager lootManager;
    private SessionManager sessionManager;
    private WorldSpawnManager worldSpawnManager;
    private MailManager mailManager;

    // Tasks
    private me.lovelyfrontier.task.WeeklyTicketTask weeklyTicketTask;

    // External Integration
    private Economy economy;

    public static LovelyFrontierPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        
        // Ensure fschematic and schematics folders exist
        File fschematicDir = new File(getDataFolder(), "fschematic");
        if (!fschematicDir.exists()) {
            fschematicDir.mkdirs();
        }
        File schematicsDir = new File(getDataFolder(), "schematics");
        if (!schematicsDir.exists()) {
            schematicsDir.mkdirs();
        }

        // 1. Initialize Utility Layers
        me.lovelyfrontier.util.MessageUtil.load(this);
        configManager = new ConfigManager(this);

        // 2. Initialize Database & Connection Pool
        try {
            databaseManager = new DatabaseManager(this, configManager);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database connection pool. Disabling plugin.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Initialize Repositories
        dungeonRepository = new DungeonRepository(this, databaseManager);
        ticketRepository = new TicketRepository(this, databaseManager);
        sessionRepository = new SessionRepository(this, databaseManager);
        instanceRepository = new InstanceRepository(this, databaseManager);
        portalRepository = new PortalRepository(this, databaseManager);
        sagaLogRepo = new SagaLogRepository(this, databaseManager);
        completionRepository = new CompletionRepository(this, databaseManager);
        playerProfileRepository = new PlayerProfileRepository(this, databaseManager);
        mailRepository = new MailRepository(this, databaseManager);

        // 4. Initialize Lock Layer
        inMemoryPortalLock = new InMemoryPortalLock();

        // 5. Initialize Managers
        dungeonManager = new DungeonManager(this);
        instanceManager = new InstanceManager(this);
        portalManager = new PortalManager(this);
        lootManager = new LootManager(this);
        sessionManager = new SessionManager(this, sessionRepository);
        worldSpawnManager = new WorldSpawnManager(this);
        worldSpawnManager.start();
        mailManager = new MailManager(this);

        // 5.5 Initialize Tasks
        weeklyTicketTask = new me.lovelyfrontier.task.WeeklyTicketTask(this);
        weeklyTicketTask.runTaskTimerAsynchronously(this, 1200L, 1200L);

        // 6. Set up Vault Economy integration (R-009 Null-safe check)
        if (!setupEconomy()) {
            getLogger().warning("Vault Economy not found! Economy rewards and ticket shop purchases will be disabled.");
        }

        // 7. Register Event Listeners
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);
        getServer().getPluginManager().registerEvents(new ChestProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new me.lovelyfrontier.gui.admin.PortalDesignerGUI(), this);
        getServer().getPluginManager().registerEvents(new me.lovelyfrontier.listener.PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new me.lovelyfrontier.listener.DungeonCommandListener(this), this);
        getServer().getPluginManager().registerEvents(new me.lovelyfrontier.listener.DungeonDeathListener(this), this);

        // 8. Register Commands
        Objects.requireNonNull(getCommand("lf")).setExecutor(new PlayerCommandManager(this));
        Objects.requireNonNull(getCommand("lf")).setTabCompleter(new PlayerCommandManager(this));
        Objects.requireNonNull(getCommand("lfa")).setExecutor(new AdminCommandManager(this));
        Objects.requireNonNull(getCommand("lfa")).setTabCompleter(new AdminCommandManager(this));

        // 9. Run Startup Cleanup (Rule R-010)
        runStartupCleanup();

        getLogger().info("LovelyFrontier Plugin has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling LovelyFrontier and cleaning up active instances...");

        if (worldSpawnManager != null) {
            worldSpawnManager.stop();
        }

        // 1. Teleport all players out of active instances to spawn (Rule R-010)
        if (instanceManager != null) {
            Location fallbackSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
            org.mvplugins.multiverse.core.world.WorldManager worldManager = null;
            try {
                if (Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core") != null) {
                    worldManager = org.mvplugins.multiverse.core.MultiverseCoreApi.get().getWorldManager();
                }
            } catch (Exception ignored) {}

            for (DungeonInstance inst : instanceManager.getActiveInstances().values()) {
                World world = Bukkit.getWorld(inst.getWorldName());
                if (world != null) {
                    for (Player player : world.getPlayers()) {
                        player.teleport(fallbackSpawn);
                        player.sendMessage("§cMáy chủ đang tải lại hoặc tắt. Bạn đã được đưa về điểm xuất phát.");
                    }
                }

                // Unload all active Multiverse worlds
                if (worldManager != null) {
                    try {
                        org.mvplugins.multiverse.core.world.WorldManager finalWm = worldManager;
                        worldManager.getLoadedWorld(inst.getWorldName()).peek(loadedWorld -> {
                            finalWm.unloadWorld(org.mvplugins.multiverse.core.world.options.UnloadWorldOptions.world(loadedWorld));
                        });
                    } catch (Exception e) {
                        getLogger().log(Level.SEVERE, "Failed to unload world " + inst.getWorldName() + " on disable", e);
                    }
                }
            }
        }

        // 2. Update all active instances to CLEANUP in database (Rule R-010)
        if (instanceRepository != null) {
            try {
                instanceRepository.markAllActiveAsCleanup().join(); // block on disable to guarantee writes finish
            } catch (Exception e) {
                getLogger().severe("Error updating instance states on disable: " + e.getMessage());
            }
        }

        // 3. Cancel all BukkitScheduler tasks (Rule R-010)
        Bukkit.getScheduler().cancelTasks(this);

        // 4. Shutdown database connection pool
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("LovelyFrontier Plugin has been disabled cleanly.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        org.bukkit.plugin.RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private void runStartupCleanup() {
        // Unload and delete any zombie world folders (Rule R-010)
        instanceRepository.getActiveInstances().thenAccept(instances -> {
            for (DungeonInstance inst : instances) {
                getLogger().info("[Startup] Cleaning up zombie instance: " + inst.getWorldName());
                
                // Multiverse unload world
                Bukkit.getScheduler().runTask(this, () -> {
                    try {
                        if (Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core") != null) {
                            org.mvplugins.multiverse.core.world.WorldManager worldManager = org.mvplugins.multiverse.core.MultiverseCoreApi.get().getWorldManager();
                            worldManager.getLoadedWorld(inst.getWorldName()).peek(loadedWorld -> {
                                worldManager.unloadWorld(org.mvplugins.multiverse.core.world.options.UnloadWorldOptions.world(loadedWorld));
                            });
                        }
                    } catch (Exception e) {
                        getLogger().warning("Could not unload zombie world: " + inst.getWorldName());
                    }

                    // Async file deletion
                    CompletableFuture.runAsync(() -> {
                        File folder = new File(Bukkit.getWorldContainer(), inst.getWorldName());
                        deleteFolder(folder);
                        instanceRepository.deleteInstance(inst.getInstanceId()).join();
                        getLogger().info("[Startup] Zombie world folder " + inst.getWorldName() + " deleted.");
                    });
                });
            }
        });

        // Cleanup expired world portals
        portalRepository.getExpiredPortals().thenAccept(portals -> {
            for (PortalRepository.DbPortal portal : portals) {
                getLogger().info("[Startup] Despawning expired world portal: " + portal.portalId);
                Bukkit.getScheduler().runTask(this, () -> {
                    removeBeaconBlocks(portal);
                    portalRepository.deletePortal(portal.portalId).join();
                });
            }
        });
    }

    private void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }

    private void removeBeaconBlocks(PortalRepository.DbPortal portal) {
        World world = Bukkit.getWorld(portal.worldName);
        if (world == null) return;

        int cx = (int) portal.x;
        int cy = (int) portal.y;
        int cz = (int) portal.z;

        // y+2: Colored Glass, y+1: Beacon, y: Iron base (3x3)
        world.getBlockAt(cx, cy + 2, cz).setType(Material.AIR);
        world.getBlockAt(cx, cy + 1, cz).setType(Material.AIR);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(cx + dx, cy, cz + dz).setType(Material.AIR);
            }
        }
    }

    // Getters
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public DungeonRepository getDungeonRepository() { return dungeonRepository; }
    public TicketRepository getTicketRepository() { return ticketRepository; }
    public SessionRepository getSessionRepository() { return sessionRepository; }
    public InstanceRepository getInstanceRepository() { return instanceRepository; }
    public PortalRepository getPortalRepository() { return portalRepository; }
    public SagaLogRepository getSagaLogRepository() { return sagaLogRepo; }
    public CompletionRepository getCompletionRepository() { return completionRepository; }
    public PlayerProfileRepository getPlayerProfileRepository() { return playerProfileRepository; }
    public MailRepository getMailRepository() { return mailRepository; }

    public InMemoryPortalLock getInMemoryPortalLock() { return inMemoryPortalLock; }

    public DungeonManager getDungeonManager() { return dungeonManager; }
    public InstanceManager getInstanceManager() { return instanceManager; }
    public PortalManager getPortalManager() { return portalManager; }
    public LootManager getLootManager() { return lootManager; }
    public SessionManager getSessionManager() { return sessionManager; }
    public WorldSpawnManager getWorldSpawnManager() { return worldSpawnManager; }
    public MailManager getMailManager() { return mailManager; }
    public me.lovelyfrontier.task.WeeklyTicketTask getWeeklyTicketTask() { return weeklyTicketTask; }

    public Economy getEconomy() { return economy; }
}
