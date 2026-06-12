package me.lovelyfrontier.listener;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.DungeonConfig;
import me.lovelyfrontier.model.DungeonInstance;
import me.lovelyfrontier.model.InstanceState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class DungeonDeathListener implements Listener {

    private final LovelyFrontierPlugin plugin;

    public DungeonDeathListener(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Intercepts damage that would result in player death inside a dungeon instance,
     * cancels the damage, and transitions the player to spectator mode.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        DungeonInstance instance = plugin.getInstanceManager().getInstanceByPlayer(player.getUniqueId());
        
        // Only trigger this within active dungeon instances
        if (instance == null || instance.getState() != InstanceState.ACTIVE) {
            return;
        }

        double finalDamage = event.getFinalDamage();
        if (finalDamage >= player.getHealth()) {
            event.setCancelled(true);

            // Transition player to spectator
            player.setGameMode(GameMode.SPECTATOR);
            
            // Restore health/food to full in spectator state
            if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            } else {
                player.setHealth(20.0);
            }
            player.setFoodLevel(20);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

            // Send death message
            player.sendMessage("§c[LovelyFrontier] Bạn đã tử trận và chuyển sang chế độ theo dõi phụ bản.");

            // Teleport to the dungeon spawn point
            DungeonConfig config = plugin.getDungeonManager().getDungeon(instance.getDungeonId());
            World world = Bukkit.getWorld(instance.getWorldName());
            if (config != null && world != null) {
                Location spawnLoc = new Location(world, config.getSpawnX(), config.getSpawnY(), config.getSpawnZ(), config.getSpawnYaw(), config.getSpawnPitch());
                player.teleport(spawnLoc);
            }
        }
    }

    /**
     * Restricts spectator movement to prevent them from flying too far from the dungeon area.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSpectatorMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SPECTATOR) {
            return;
        }

        DungeonInstance instance = plugin.getInstanceManager().getInstanceByPlayer(player.getUniqueId());
        if (instance == null || instance.getState() != InstanceState.ACTIVE) {
            return;
        }

        DungeonConfig config = plugin.getDungeonManager().getDungeon(instance.getDungeonId());
        World world = Bukkit.getWorld(instance.getWorldName());
        if (config != null && world != null) {
            Location spawnLoc = new Location(world, config.getSpawnX(), config.getSpawnY(), config.getSpawnZ());
            
            if (event.getTo() != null && event.getTo().getWorld().equals(world)) {
                // If they fly more than 150 blocks away from spawn, reset them
                if (event.getTo().distanceSquared(spawnLoc) > 22500.0) {
                    event.setCancelled(true);
                    
                    Location teleportSpawn = new Location(world, config.getSpawnX(), config.getSpawnY(), config.getSpawnZ(), config.getSpawnYaw(), config.getSpawnPitch());
                    player.teleport(teleportSpawn);
                    player.sendMessage("§c[LovelyFrontier] Bạn không thể di chuyển quá xa khu vực phụ bản.");
                }
            }
        }
    }

    /**
     * Restricts spectators from using teleport spectator mode to teleport out of bounds or to other players.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSpectateTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            Player player = event.getPlayer();
            DungeonInstance instance = plugin.getInstanceManager().getInstanceByPlayer(player.getUniqueId());
            if (instance != null) {
                event.setCancelled(true);
                player.sendMessage("§c[LovelyFrontier] Tính năng dịch chuyển tự động bị vô hiệu hóa trong phụ bản.");
            }
        }
    }
}
