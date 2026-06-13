package me.lovelyfrontier.listener;

import me.lovelyfrontier.LovelyFrontierPlugin;
import org.bukkit.Bukkit;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerConnectionListener implements Listener {

    private final LovelyFrontierPlugin plugin;
    private static final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();

    public PlayerConnectionListener(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
        startPlaytimeAutoSave();
    }

    /**
     * Get the active session playtime for an online player in hours.
     */
    public static double getOnlineSessionHours(UUID uuid) {
        Long joinTime = joinTimes.get(uuid);
        if (joinTime == null) return 0.0;
        return (System.currentTimeMillis() - joinTime) / 3600000.0;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        joinTimes.put(uuid, System.currentTimeMillis());

        // Alt Detection (IP hashing with SHA-256)
        if (player.getAddress() != null) {
            String ip = player.getAddress().getAddress().getHostAddress();
            String ipHash = sha256(ip);
            plugin.getPlayerProfileRepository().registerPlayerIp(uuid, ipHash).thenAccept(count -> {
                int maxAccounts = plugin.getConfigManager().getAntiAbuseMaxAccountsPerIp();
                if (count > maxAccounts) {
                    plugin.getPlayerProfileRepository().setFlagged(uuid, true).thenAccept(success -> {
                        plugin.getLogger().warning("[LF] Player " + player.getName() + " flagged for alt account limit! Sharing IP with " + count + " accounts.");
                    });
                }
            });
        }

        // Reconnection Grace handling
        me.lovelyfrontier.model.DungeonInstance instance = plugin.getInstanceManager().getInstanceByPlayer(uuid);
        if (instance != null && instance.getState() == me.lovelyfrontier.model.InstanceState.ACTIVE) {
            if (instance.getDisconnectedMembers().containsKey(uuid)) {
                instance.getDisconnectedMembers().remove(uuid);
                plugin.getInstanceRepository().updateMemberConnection(instance.getInstanceId(), uuid, true);

                rescaleBossHp(instance);

                org.bukkit.World world = Bukkit.getWorld(instance.getWorldName());
                me.lovelyfrontier.model.DungeonConfig config = plugin.getDungeonManager().getDungeon(instance.getDungeonId());
                if (world != null && config != null) {
                    org.bukkit.Location spawnLoc = new org.bukkit.Location(world, config.getSpawnX(), config.getSpawnY(), config.getSpawnZ(), config.getSpawnYaw(), config.getSpawnPitch());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            player.teleport(spawnLoc);
                            player.sendMessage(me.lovelyfrontier.util.MessageUtil.get("dungeon_ready"));
                        }
                    }, 5L);
                }
            }
        }

        // Deliver Mail on Login
        plugin.getMailManager().deliverMailOnLogin(player);

        // Weekly Free Ticket Check
        if (plugin.getWeeklyTicketTask() != null) {
            plugin.getWeeklyTicketTask().checkTicket(player);
        }
    }

    private static final Map<UUID, String> playerActiveWorldPortalIn = new ConcurrentHashMap<>();

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        playerActiveWorldPortalIn.remove(uuid);
        Long joinTime = joinTimes.remove(uuid);
        if (joinTime != null) {
            double sessionHours = (System.currentTimeMillis() - joinTime) / 3600000.0;
            plugin.getPlayerProfileRepository().incrementPlaytime(uuid, sessionHours);
        }

        // Grace period / disconnect handling
        me.lovelyfrontier.model.DungeonInstance instance = plugin.getInstanceManager().getInstanceByPlayer(uuid);
        if (instance != null && instance.getState() == me.lovelyfrontier.model.InstanceState.ACTIVE) {
            instance.getDisconnectedMembers().put(uuid, System.currentTimeMillis());
            plugin.getInstanceRepository().updateMemberConnection(instance.getInstanceId(), uuid, false);

            rescaleBossHp(instance);

            int graceSeconds = plugin.getConfigManager().getInstanceDisconnectGrace();
            String instanceId = instance.getInstanceId();

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                me.lovelyfrontier.model.DungeonInstance inst = plugin.getInstanceManager().getInstance(instanceId);
                if (inst != null && inst.getDisconnectedMembers().containsKey(uuid)) {
                    // Grace period expired!
                    inst.getDisconnectedMembers().remove(uuid);
                    inst.getMembers().remove(uuid);

                    // If boss_cleared = TRUE, queue loot to lf_mail
                    if (inst.isBossCleared()) {
                        double rewardAmount = 100.0; // standard reward amount, or could load config
                        plugin.getMailManager().queueLoot(uuid, inst.getDungeonId(), rewardAmount, new org.bukkit.inventory.ItemStack[0]);
                    }

                    // If all members are gone, clean up
                    boolean anyoneLeft = false;
                    for (UUID memberUuid : inst.getMembers()) {
                        if (!inst.getDisconnectedMembers().containsKey(memberUuid)) {
                            anyoneLeft = true;
                            break;
                        }
                    }
                    if (!anyoneLeft) {
                        plugin.getLogger().info("All members have left or timed out from instance " + instanceId + ". Triggering cleanup.");
                        plugin.getInstanceManager().beginCleanup(instanceId);
                    }
                }
            }, graceSeconds * 20L);
        }
    }

    private void rescaleBossHp(me.lovelyfrontier.model.DungeonInstance instance) {
        int total = instance.getMembers().size();
        int disconnected = instance.getDisconnectedMembers().size();
        int connected = total - disconnected;
        if (connected <= 0 || total <= 0) return;

        double targetFactor = (double) connected / total;

        try {
            if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
                org.bukkit.World world = Bukkit.getWorld(instance.getWorldName());
                if (world != null) {
                    for (org.bukkit.entity.Entity entity : world.getEntities()) {
                        if (entity instanceof org.bukkit.entity.LivingEntity living) {
                            if (io.lumine.mythic.bukkit.MythicBukkit.inst().getAPIHelper().isMythicMob(entity)) {
                                rescaleBossHealth(instance, living, targetFactor);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[LF] Error scaling MythicMobs boss HP: " + t.getMessage());
        }
    }

    private void rescaleBossHealth(me.lovelyfrontier.model.DungeonInstance instance,
                                   org.bukkit.entity.LivingEntity living,
                                   double targetFactor) {
        AttributeInstance maxHealthAttribute = living.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttribute == null) {
            return;
        }

        double currentMax = maxHealthAttribute.getBaseValue();
        double baseMax = instance.getBossBaseMaxHealth().computeIfAbsent(living.getUniqueId(), ignored -> currentMax);
        if (baseMax <= 0.0) {
            return;
        }

        double currentPercent = currentMax > 0.0 ? living.getHealth() / currentMax : 1.0;
        double newMax = Math.max(1.0, baseMax * targetFactor);
        maxHealthAttribute.setBaseValue(newMax);
        living.setHealth(Math.min(newMax, Math.max(0.0, newMax * currentPercent)));
    }

    @EventHandler
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ() && from.getBlockY() == to.getBlockY()) {
            return;
        }

        if (plugin.getWorldSpawnManager() == null) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Map<String, me.lovelyfrontier.repository.PortalRepository.DbPortal> activePortals = plugin.getWorldSpawnManager().getActiveWorldPortals();
        if (activePortals.isEmpty()) {
            playerActiveWorldPortalIn.remove(uuid);
            return;
        }

        String closePortalId = null;
        me.lovelyfrontier.repository.PortalRepository.DbPortal closePortal = null;
        for (Map.Entry<String, me.lovelyfrontier.repository.PortalRepository.DbPortal> entry : activePortals.entrySet()) {
            me.lovelyfrontier.repository.PortalRepository.DbPortal portal = entry.getValue();
            if (portal.worldName.equalsIgnoreCase(to.getWorld().getName())) {
                double dx = portal.x - to.getX();
                double dy = portal.y - to.getY();
                double dz = portal.z - to.getZ();
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq <= 25.0) { // 5 blocks radius
                    closePortalId = entry.getKey();
                    closePortal = portal;
                    break;
                }
            }
        }

        if (closePortalId != null) {
            String currentPortalId = playerActiveWorldPortalIn.get(uuid);
            if (currentPortalId == null || !currentPortalId.equals(closePortalId)) {
                playerActiveWorldPortalIn.put(uuid, closePortalId);
                String dungeonId = closePortal.dungeonId;
                me.lovelyfrontier.model.DungeonConfig dungeon = plugin.getDungeonManager().getDungeon(dungeonId);
                String dungeonName = dungeon != null ? dungeon.getName() : dungeonId;

                List<String> items = new java.util.ArrayList<>();
                // Always add Eye of Ender as the default fallback
                items.add("Eye of Ender");

                for (me.lovelyfrontier.model.PortalType pt : plugin.getPortalManager().getPortalTypes().values()) {
                    if (pt.getAction().equalsIgnoreCase("SPECIFIC:" + dungeonId) ||
                        pt.getAction().equalsIgnoreCase("RANDOM_DUNGEON") ||
                        pt.getAction().equalsIgnoreCase("OPEN_MENU")) {
                        if (pt.getTriggerItem() != null && pt.getTriggerItem() != org.bukkit.Material.AIR) {
                            String displayName = pt.getTriggerItem().name().replace("_", " ").toLowerCase();
                            if (pt.getTriggerItem() == org.bukkit.Material.ENDER_EYE) {
                                displayName = "Eye of Ender";
                            }
                            
                            // Format nicely (e.g. Capitalize words)
                            String[] words = displayName.split(" ");
                            StringBuilder sb = new StringBuilder();
                            for (String w : words) {
                                if (!w.isEmpty()) {
                                    sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
                                }
                            }
                            String formattedName = sb.toString().trim();
                            if (!items.contains(formattedName)) {
                                items.add(formattedName);
                            }
                        }
                    }
                }

                String itemsStr = String.join(", ", items);
                player.sendMessage(me.lovelyfrontier.util.MessageUtil.get("proximity_guidance", "dungeon", dungeonName, "item", itemsStr));
            }
        } else {
            playerActiveWorldPortalIn.remove(uuid);
        }
    }

    /**
     * Periodically save playtime for online players to the database (every 10 minutes)
     */
    private void startPlaytimeAutoSave() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            for (UUID uuid : joinTimes.keySet()) {
                // Atomic replace to get the previous join time and set to now
                Long prevJoin = joinTimes.put(uuid, now);
                if (prevJoin != null) {
                    double increment = (now - prevJoin) / 3600000.0;
                    plugin.getPlayerProfileRepository().incrementPlaytime(uuid, increment);
                }
            }
        }, 12000L, 12000L); // 12000 ticks = 10 minutes
    }

    private String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
