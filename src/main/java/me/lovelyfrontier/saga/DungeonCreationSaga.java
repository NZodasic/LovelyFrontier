package me.lovelyfrontier.saga;

import org.mvplugins.multiverse.core.MultiverseCore;
import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.*;
import me.lovelyfrontier.repository.*;
import me.lovelyfrontier.manager.InstanceManager;
import me.lovelyfrontier.manager.LootManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;

public class DungeonCreationSaga {

    private final LovelyFrontierPlugin plugin;
    private final PlayerSession session;
    private final String sagaId;
    private final String instanceId;
    private final String worldName;
    private final Deque<Runnable> compensationStack = new ConcurrentLinkedDeque<>();

    public DungeonCreationSaga(LovelyFrontierPlugin plugin, PlayerSession session) {
        this.plugin = plugin;
        this.session = session;
        this.sagaId = UUID.randomUUID().toString();
        this.instanceId = UUID.randomUUID().toString();
        this.worldName = "lf_instance_" + instanceId;
    }

    /**
     * Executes the dungeon creation saga.
     * Returns a CompletableFuture containing the instanceId if successful, or null on failure.
     */
    public CompletableFuture<String> execute() {
        SagaLogRepository sagaLogRepo = plugin.getSagaLogRepository();
        
        plugin.getLogger().info("[Saga] Starting dungeon creation saga " + sagaId + " for dungeon " + session.getDungeonId());
        
        return sagaLogRepo.logStep(sagaId, instanceId, "START", "RUNNING", null)
            .thenCompose(v -> step1ConsumeTicket())
            .thenCompose(success -> {
                if (!success) throw new RuntimeException("Step 1 (Consume Ticket) failed.");
                return step2CreateWorldAndPaste();
            })
            .thenCompose(success -> {
                if (!success) throw new RuntimeException("Step 2 (World Creation/Paste) failed.");
                return step3ReserveSlot();
            })
            .thenCompose(success -> {
                if (!success) throw new RuntimeException("Step 3 (Reserve Slot) failed.");
                return step4CreateInstanceRecord();
            })
            .thenCompose(success -> {
                if (!success) throw new RuntimeException("Step 4 (Create Record) failed.");
                return step5TeleportPlayers();
            })
            .thenCompose(success -> {
                if (!success) throw new RuntimeException("Step 5 (Teleport Players) failed.");
                return step6ShuffleLootChests();
            })
            .thenCompose(success -> {
                if (!success) throw new RuntimeException("Step 6 (Shuffle Loot) failed.");
                return step7StartTimer();
            })
            .thenCompose(success -> {
                if (!success) throw new RuntimeException("Step 7 (Start Timer) failed.");
                return step8MarkSessionConsumed();
            })
            .thenCompose(success -> {
                if (!success) throw new RuntimeException("Step 8 (Mark Consumed) failed.");
                return sagaLogRepo.logStep(sagaId, instanceId, "COMPLETE", "SUCCESS", null)
                        .thenApply(v -> {
                            if (session.getPortalId() != null && plugin.getWorldSpawnManager() != null) {
                                plugin.getWorldSpawnManager().despawnPortal(session.getPortalId(), false);
                            }
                            return instanceId;
                        });
            })
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "[Saga] Saga failed: " + ex.getMessage(), ex);
                sagaLogRepo.logStep(sagaId, instanceId, "FAILURE", "FAILED", ex.getMessage());
                rollback();
                return null;
            });
    }

    /**
     * Step 1: Consume Ticket.
     */
    private CompletableFuture<Boolean> step1ConsumeTicket() {
        if (session.isBypassTicket()) {
            plugin.getLogger().info("[Saga] Bypassing ticket consumption for admin/force session.");
            return CompletableFuture.completedFuture(true);
        }

        TicketRepository ticketRepo = plugin.getTicketRepository();
        UUID leader = session.getLeaderUuid();
        String dungeon = session.getDungeonId();
        String diff = session.getDifficulty();

        return ticketRepo.findValidTicket(leader, dungeon, diff).thenCompose(ticketId -> {
            if (ticketId == null) {
                plugin.getLogger().warning("[Saga] No valid ticket found for " + leader + " to dungeon " + dungeon);
                return CompletableFuture.completedFuture(false);
            }
            return ticketRepo.consumeTicket(ticketId).thenApply(consumed -> {
                if (consumed) {
                    plugin.getLogger().info("[Saga] Ticket " + ticketId + " consumed.");
                    // Register refund ticket compensation
                    compensationStack.push(() -> {
                        plugin.getLogger().info("[Saga-Comp] Refunding ticket " + ticketId);
                        ticketRepo.refundTicket(ticketId, leader, dungeon, diff).join();
                    });
                    return true;
                }
                return false;
            });
        });
    }

    /**
     * Step 2: Create world and paste schematic.
     */
    private CompletableFuture<Boolean> step2CreateWorldAndPaste() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Multiverse world creation must happen on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core") == null) {
                    plugin.getLogger().severe("[Saga] Multiverse-Core not found!");
                    future.complete(false);
                    return;
                }

                org.mvplugins.multiverse.core.world.WorldManager worldManager = org.mvplugins.multiverse.core.MultiverseCoreApi.get().getWorldManager();

                // Create void or flat world using Multiverse v5.x options
                org.mvplugins.multiverse.core.world.options.CreateWorldOptions options = 
                        org.mvplugins.multiverse.core.world.options.CreateWorldOptions.worldName(worldName)
                        .environment(World.Environment.NORMAL)
                        .worldType(org.bukkit.WorldType.FLAT)
                        .generateStructures(false);

                org.mvplugins.multiverse.core.utils.result.Attempt<org.mvplugins.multiverse.core.world.LoadedMultiverseWorld, org.mvplugins.multiverse.core.world.reasons.CreateFailureReason> attempt = worldManager.createWorld(options);

                if (!attempt.isSuccess()) {
                    plugin.getLogger().severe("[Saga] Multiverse failed to create world " + worldName + ": " + attempt.getFailureMessage());
                    future.complete(false);
                    return;
                }

                // Register compensation to unload and delete world
                compensationStack.push(() -> {
                    plugin.getLogger().info("[Saga-Comp] Deleting world " + worldName);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            worldManager.getLoadedWorld(worldName).peek(loadedWorld -> {
                                worldManager.unloadWorld(org.mvplugins.multiverse.core.world.options.UnloadWorldOptions.world(loadedWorld));
                            });
                            CompletableFuture.runAsync(() -> {
                                File folder = new File(Bukkit.getWorldContainer(), worldName);
                                deleteFolder(folder);
                            });
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.SEVERE, "[Saga-Comp] Error deleting world " + worldName, e);
                        }
                    });
                });

                // Paste Schematic Async (Rule R-001)
                DungeonConfig config = plugin.getDungeonManager().getDungeon(session.getDungeonId());
                if (config == null) {
                    future.complete(false);
                    return;
                }

                File schemFile = new File(plugin.getDataFolder(), "schematics/" + config.getSchematicPath());
                if (!schemFile.exists()) {
                    plugin.getLogger().severe("[Saga] Schematic file not found: " + schemFile.getPath());
                    future.complete(false);
                    return;
                }

                // Call WorldEdit paste
                // We load the schematic file asynchronously (Rule R-001: ALL file I/O MUST be async)
                CompletableFuture.runAsync(() -> {
                    try {
                        com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat format = 
                                com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats.findByFile(schemFile);
                        if (format == null) {
                            throw new Exception("Unknown clipboard format for file: " + schemFile.getName());
                        }

                        com.sk89q.worldedit.extent.clipboard.Clipboard clipboard;
                        try (com.sk89q.worldedit.extent.clipboard.io.ClipboardReader reader = format.getReader(new java.io.FileInputStream(schemFile))) {
                            clipboard = reader.read();
                        }

                        // Schedule the synchronous paste operation on the main thread (Rule R-001 & R-009)
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                org.bukkit.World bukkitWorld = Bukkit.getWorld(worldName);
                                if (bukkitWorld == null) {
                                    throw new Exception("World " + worldName + " not found!");
                                }
                                com.sk89q.worldedit.world.World worldEditWorld = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(bukkitWorld);
                                try (com.sk89q.worldedit.EditSession editSession = com.sk89q.worldedit.WorldEdit.getInstance().newEditSession(worldEditWorld)) {
                                    com.sk89q.worldedit.function.operation.Operation operation = new com.sk89q.worldedit.session.ClipboardHolder(clipboard)
                                            .createPaste(editSession)
                                            .to(com.sk89q.worldedit.math.BlockVector3.at(0, 64, 0))
                                            .ignoreAirBlocks(false)
                                            .build();
                                    com.sk89q.worldedit.function.operation.Operations.complete(operation);
                                }
                                future.complete(true);
                            } catch (Throwable e) {
                                plugin.getLogger().log(Level.SEVERE, "[Saga] WorldEdit paste error: " + e.getMessage(), e);
                                future.complete(false);
                            }
                        });
                    } catch (Throwable e) {
                        plugin.getLogger().log(Level.SEVERE, "[Saga] Schematic load error: " + e.getMessage(), e);
                        future.complete(false);
                    }
                });

            } catch (Throwable e) {
                plugin.getLogger().log(Level.SEVERE, "[Saga] Error creating world: " + e.getMessage(), e);
                future.complete(false);
            }
        });
        
        return future;
    }

    /**
     * Step 3: Reserve Slot.
     */
    private CompletableFuture<Boolean> step3ReserveSlot() {
        InstanceManager instManager = plugin.getInstanceManager();
        DungeonInstance instance = new DungeonInstance(instanceId, session.getDungeonId(), session.getDifficulty(), worldName);
        instance.getMembers().addAll(session.getPartyMembers());

        // Try registering inside InstanceManager
        boolean success = instManager.registerInstance(instance);
        if (success) {
            compensationStack.push(() -> {
                plugin.getLogger().info("[Saga-Comp] Releasing instance slot for " + instanceId);
                instManager.unregisterInstance(instanceId);
            });
            return CompletableFuture.completedFuture(true);
        } else {
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Step 4: Create Instance Record.
     */
    private CompletableFuture<Boolean> step4CreateInstanceRecord() {
        InstanceRepository instRepo = plugin.getInstanceRepository();
        DungeonInstance instance = plugin.getInstanceManager().getInstance(instanceId);
        
        return instRepo.createInstance(instance).thenApply(success -> {
            if (success) {
                compensationStack.push(() -> {
                    plugin.getLogger().info("[Saga-Comp] Deleting instance record for " + instanceId);
                    instRepo.deleteInstance(instanceId).join();
                });
                return true;
            }
            return false;
        });
    }

    /**
     * Step 5: Teleport Players.
     */
    private CompletableFuture<Boolean> step5TeleportPlayers() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Must happen on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    future.complete(false);
                    return;
                }

                DungeonConfig config = plugin.getDungeonManager().getDungeon(session.getDungeonId());
                if (config == null) {
                    future.complete(false);
                    return;
                }
                Location spawnLoc = new Location(world, config.getSpawnX(), config.getSpawnY(), config.getSpawnZ(), config.getSpawnYaw(), config.getSpawnPitch());

                DungeonInstance instance = plugin.getInstanceManager().getInstance(instanceId);
                if (instance == null) {
                    future.complete(false);
                    return;
                }

                int teleported = 0;
                for (UUID uuid : session.getPartyMembers()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        if (player.teleport(spawnLoc)) {
                            teleported++;
                        }
                    }
                }

                if (teleported == 0) {
                    future.complete(false);
                    return;
                }

                // Update active state after at least one player entered the instance.
                instance.setState(InstanceState.ACTIVE);
                plugin.getInstanceRepository().updateState(instanceId, InstanceState.ACTIVE);
                future.complete(true);
            } catch (Throwable e) {
                plugin.getLogger().log(Level.SEVERE, "[Saga] Teleport error: " + e.getMessage(), e);
                future.complete(false);
            }
        });

        return future;
    }

    /**
     * Step 6: Shuffle and fill chests.
     */
    private CompletableFuture<Boolean> step6ShuffleLootChests() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Execute with 1 tick delay (Spec Section 5 & 6)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                LootManager lootManager = plugin.getLootManager();
                lootManager.cloneChestsToInstance(session.getDungeonId(), instanceId, worldName)
                        .thenCompose(cloned -> {
                            if (!cloned) {
                                return CompletableFuture.completedFuture(false);
                            }
                            return lootManager.populateInstanceChests(instanceId);
                        })
                        .thenAccept(success -> future.complete(success))
                        .exceptionally(ex -> {
                            plugin.getLogger().log(Level.SEVERE, "[Saga] Shuffling chests failed: " + ex.getMessage(), ex);
                            future.complete(false);
                            return null;
                        });
            } catch (Throwable e) {
                plugin.getLogger().log(Level.SEVERE, "[Saga] Shuffle loot error: " + e.getMessage(), e);
                future.complete(false);
            }
        }, 1L);

        return future;
    }

    /**
     * Step 7: Start timer.
     */
    private CompletableFuture<Boolean> step7StartTimer() {
        DungeonConfig config = plugin.getDungeonManager().getDungeon(session.getDungeonId());
        if (config == null) {
            return CompletableFuture.completedFuture(false);
        }
        plugin.getInstanceManager().startInstanceTimer(instanceId, config.getTimeLimitSeconds());
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Step 8: Persist session idempotency state.
     */
    private CompletableFuture<Boolean> step8MarkSessionConsumed() {
        return plugin.getSessionRepository().markConsumed(session.getSessionId(), instanceId);
    }

    /**
     * Rollback the saga execution by executing compensating transactions in LIFO order.
     */
    private void rollback() {
        plugin.getLogger().warning("[Saga] Initiating rollback for saga " + sagaId + "...");
        while (!compensationStack.isEmpty()) {
            Runnable comp = compensationStack.pop();
            try {
                comp.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[Saga] Compensation failed: " + e.getMessage(), e);
            }
        }
        plugin.getLogger().warning("[Saga] Rollback complete.");
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
