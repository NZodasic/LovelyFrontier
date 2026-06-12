package me.lovelyfrontier.manager;

import org.mvplugins.multiverse.core.MultiverseCore;
import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.DungeonInstance;
import me.lovelyfrontier.model.InstanceState;
import me.lovelyfrontier.repository.InstanceRepository;
import me.lovelyfrontier.util.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class InstanceManager {

    private final LovelyFrontierPlugin plugin;
    private final Map<String, DungeonInstance> activeInstances = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> activeTimers = new ConcurrentHashMap<>();

    public InstanceManager(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean registerInstance(DungeonInstance instance) {
        if (activeInstances.size() >= plugin.getConfigManager().getInstanceMaxActive()) {
            plugin.getLogger().warning("Cannot register instance " + instance.getInstanceId() + ": Max active instance limit reached.");
            return false;
        }
        activeInstances.put(instance.getInstanceId(), instance);
        return true;
    }

    public void unregisterInstance(String instanceId) {
        activeInstances.remove(instanceId);
        BukkitTask timer = activeTimers.remove(instanceId);
        if (timer != null) {
            timer.cancel();
        }
    }

    public DungeonInstance getInstance(String instanceId) {
        return activeInstances.get(instanceId);
    }

    public DungeonInstance getInstanceByPlayer(UUID playerUuid) {
        for (DungeonInstance instance : activeInstances.values()) {
            if (instance.getMembers().contains(playerUuid)) {
                return instance;
            }
        }
        return null;
    }

    public Map<String, DungeonInstance> getActiveInstances() {
        return activeInstances;
    }

    /**
     * Starts the time limit scheduler for a dungeon instance.
     */
    public void startInstanceTimer(String instanceId, int timeLimitSeconds) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().info("Time expired for instance " + instanceId + ". Initiating cleanup.");
            beginCleanup(instanceId);
        }, timeLimitSeconds * 20L);
        activeTimers.put(instanceId, task);
    }

    /**
     * Step 1 of cleanup: COMPLETING. Distributes rewards, records leaderboards,
     * and sets a 30-second grace period for players to grab loot.
     */
    public void beginCleanup(String instanceId) {
        DungeonInstance instance = activeInstances.get(instanceId);
        if (instance == null || instance.getState() == InstanceState.COMPLETING || instance.getState() == InstanceState.CLEANUP) {
            return;
        }

        instance.setState(InstanceState.COMPLETING);
        plugin.getInstanceRepository().updateState(instanceId, InstanceState.COMPLETING);
        
        // Cancel active time limit task
        BukkitTask timer = activeTimers.remove(instanceId);
        if (timer != null) {
            timer.cancel();
        }

        // Notify players
        World world = Bukkit.getWorld(instance.getWorldName());
        if (world != null) {
            for (Player player : world.getPlayers()) {
                player.sendMessage(MessageUtil.get("instance_cleanup_start", "seconds", 30));
            }
        }

        // Distribute rewards (Vault Economy Async)
        distributeRewards(instance);

        // Update Leaderboard Completion Record (Async)
        recordLeaderboard(instance);

        // Start 30-second grace timer for physical cleanup (Step 2)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            executeCleanup(instanceId);
        }, 600L); // 30 seconds (600 ticks)
    }

    /**
     * Step 2 of cleanup: CLEANUP. Teleports remaining players out,
     * unloads the Multiverse-Core world, and deletes files asynchronously.
     */
    public void executeCleanup(String instanceId) {
        DungeonInstance instance = activeInstances.remove(instanceId);
        if (instance == null) return;

        instance.setState(InstanceState.CLEANUP);

        // Teleport remaining players out (Main thread)
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(instance.getWorldName());
            Location exitLoc = Bukkit.getWorlds().get(0).getSpawnLocation(); // default fallback spawn

            if (world != null) {
                for (Player player : world.getPlayers()) {
                    player.teleport(exitLoc);
                    player.sendMessage(MessageUtil.get("instance_left"));
                }
            }

            // Unload world using Multiverse-Core (Main thread)
            try {
                if (Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core") != null) {
                    org.mvplugins.multiverse.core.world.WorldManager worldManager = org.mvplugins.multiverse.core.MultiverseCoreApi.get().getWorldManager();
                    worldManager.getLoadedWorld(instance.getWorldName()).peek(loadedWorld -> {
                        worldManager.unloadWorld(org.mvplugins.multiverse.core.world.options.UnloadWorldOptions.world(loadedWorld));
                    });
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[LF] Error unloading world " + instance.getWorldName() + " via Multiverse", e);
            }

            // Delete world folder asynchronously
            CompletableFuture.runAsync(() -> {
                File worldFolder = new File(Bukkit.getWorldContainer(), instance.getWorldName());
                deleteFolder(worldFolder);
                
                // Update DB state to CLEANUP
                plugin.getInstanceRepository().deleteInstance(instanceId).join();
                plugin.getLogger().info("[LF] Dungeon world " + instance.getWorldName() + " deleted and database cleaned up.");
            });
        });
    }

    private void distributeRewards(DungeonInstance instance) {
        // Run Vault API calls asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                Economy economy = plugin.getEconomy();
                if (economy == null) {
                    plugin.getLogger().warning("[LF] Vault economy not found! Skipping reward distribution.");
                    return;
                }

                // If boss was cleared, reward all members who completed
                if (instance.isBossCleared()) {
                    double rewardAmount = 100.0; // Configurable or depending on difficulty
                    for (UUID uuid : instance.getMembers()) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            economy.depositPlayer(player, rewardAmount);
                            player.sendMessage(MessageUtil.get("shop_success", "price", rewardAmount)); // repurposing success msg
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[LF] External Vault API error: " + e.getMessage(), e);
            }
        });
    }

    private void recordLeaderboard(DungeonInstance instance) {
        if (!instance.isBossCleared()) return;
        
        long durationSeconds = (System.currentTimeMillis() - instance.getCreatedAt()) / 1000L;
        plugin.getCompletionRepository().saveCompletion(
                instance.getDungeonId(),
                instance.getDifficulty(),
                instance.getMembers().size(),
                (int) durationSeconds
        ).thenAccept(success -> {
            if (success) {
                plugin.getLogger().info("[LF] Leaderboard entry saved for instance " + instance.getInstanceId());
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
}
