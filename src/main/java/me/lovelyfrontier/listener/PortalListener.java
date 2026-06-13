package me.lovelyfrontier.listener;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.PortalType;
import me.lovelyfrontier.repository.PortalRepository;
import me.lovelyfrontier.util.MessageUtil;
import me.lovelyfrontier.gui.DifficultyGUI;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.concurrent.atomic.AtomicBoolean;

public class PortalListener implements Listener {

    private final LovelyFrontierPlugin plugin;

    public PortalListener(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
    }

    private void handlePortalTrigger(Player player, PortalType portalType, Location centerLoc, String matchedWorldPortalId, String matchedWorldPortalDungeonId) {
        if (centerLoc == null || centerLoc.getWorld() == null) {
            return;
        }

        // Check: is player a party leader?
        if (!isPartyLeader(player)) {
            player.sendMessage(MessageUtil.get("only_leader_portal"));
            return;
        }

        // Check if solo is allowed
        List<UUID> partyMembers = getPartyMembers(player);
        if (partyMembers.size() <= 1 && !plugin.getConfigManager().isPartyAllowSolo()) {
            player.sendMessage(MessageUtil.get("solo_not_allowed"));
            return;
        }

        // Check party size
        int minSize = plugin.getConfigManager().getPartyMinSize();
        if (partyMembers.size() < minSize) {
            player.sendMessage(MessageUtil.get("need_more_players", "count", minSize));
            return;
        }

        // Acquire locks
        String portalId = matchedWorldPortalId != null ? matchedWorldPortalId : (centerLoc.getWorld().getName() + ":" + centerLoc.getBlockX() + ":" + centerLoc.getBlockY() + ":" + centerLoc.getBlockZ());

        // Layer 1 Lock (In-Memory)
        if (!plugin.getInMemoryPortalLock().tryLock(portalId, player.getUniqueId())) {
            player.sendMessage(MessageUtil.get("portal_locked"));
            return;
        }

        if (matchedWorldPortalId == null) {
            openDifficultyGui(player, portalType, null, portalId, false);
            return;
        }

        AtomicBoolean dbLockAcquired = new AtomicBoolean(false);
        plugin.getPortalRepository().tryAcquireLock(portalId, player.getUniqueId()).thenAccept(dbLocked -> {
            dbLockAcquired.set(dbLocked);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    releasePortalLock(portalId, dbLocked);
                    return;
                }

                if (!dbLocked) {
                    releasePortalLock(portalId, false);
                    player.sendMessage(MessageUtil.get("portal_locked"));
                    return;
                }

                openDifficultyGui(player, portalType, matchedWorldPortalDungeonId, portalId, true);
            });
        }).exceptionally(ex -> {
            releasePortalLock(portalId, dbLockAcquired.get());
            plugin.getLogger().log(Level.SEVERE, "[LF] Database lock failed", ex);
            return null;
        });
    }

    private void openDifficultyGui(Player player, PortalType portalType, String dungeonId, String portalId, boolean dbLocked) {
        if (dungeonId != null) {
            DifficultyGUI.openForDungeon(player, dungeonId, portalId);
        } else {
            DifficultyGUI.open(player, portalType, portalId);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getSessionManager().findActiveSessionByPlayer(player.getUniqueId()) == null) {
                releasePortalLock(portalId, dbLocked);
            }
        }, 20L * 30L);
    }

    private void releasePortalLock(String portalId, boolean dbLocked) {
        if (portalId != null) {
            plugin.getInMemoryPortalLock().unlock(portalId);
            if (dbLocked) {
                plugin.getPortalRepository().releaseLock(portalId);
            }
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (event.getItemDrop() == null || event.getItemDrop().getItemStack() == null) return;
        Material droppedType = event.getItemDrop().getItemStack().getType();

        // 1. Check if dropped item matches any registered portal's trigger item (fast check)
        boolean isTrigger = false;
        for (PortalType pt : plugin.getPortalManager().getPortalTypes().values()) {
            if (pt.getTriggerItem() == droppedType && (pt.getTriggerMethod() == null || pt.getTriggerMethod().equalsIgnoreCase("DROP"))) {
                isTrigger = true;
                break;
            }
        }

        if (!isTrigger) return;

        Location loc = event.getItemDrop().getLocation().clone().subtract(0, 1, 0);
        World world = loc.getWorld();
        if (world == null) return;

        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();

        Map<String, Material> blockCache = new HashMap<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                blockCache.put(dx + "," + dz, world.getBlockAt(cx + dx, cy, cz + dz).getType());
            }
        }

        PortalType portalType = plugin.getPortalManager().detectPatternNow(blockCache);
        if (portalType == null || (portalType.getTriggerMethod() != null && !portalType.getTriggerMethod().equalsIgnoreCase("DROP"))) {
            return;
        }

        event.setCancelled(true);
        handlePortalTrigger(player, portalType, loc, null, null);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            PortalRepository.DbPortal worldPortal = findWorldPortal(clicked);
            if (worldPortal != null) {
                event.setCancelled(true);
                Location portalBase = new Location(clicked.getWorld(), worldPortal.x, worldPortal.y, worldPortal.z);
                handlePortalTrigger(player, null, portalBase, worldPortal.portalId, worldPortal.dungeonId);
                return;
            }
        }

        if (isWorldPortalStructureBlock(clicked)) {
            return;
        }

        if (clicked.getType() == Material.BEACON || clicked.getType() == Material.IRON_BLOCK) {
            return;
        }

        String method;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            method = "RIGHT_CLICK";
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            method = "LEFT_CLICK";
        } else {
            return;
        }

        org.bukkit.inventory.ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null) return;
        Material itemType = handItem.getType();
        if (itemType == Material.AIR) return;

        // Check if there are any portal types matching this item and method
        boolean isTrigger = false;
        for (PortalType pt : plugin.getPortalManager().getPortalTypes().values()) {
            if (pt.getTriggerItem() == itemType && pt.getTriggerMethod() != null && pt.getTriggerMethod().equalsIgnoreCase(method)) {
                isTrigger = true;
                break;
            }
        }
        if (!isTrigger) return;

        Location loc = event.getClickedBlock().getLocation();
        World world = loc.getWorld();
        if (world == null) return;
        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();

        Map<String, Material> blockCache = new HashMap<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                blockCache.put(dx + "," + dz, world.getBlockAt(cx + dx, cy, cz + dz).getType());
            }
        }

        PortalType portalType = plugin.getPortalManager().detectPatternNow(blockCache);
        if (portalType == null || portalType.getTriggerMethod() == null || !portalType.getTriggerMethod().equalsIgnoreCase(method)) {
            return;
        }

        event.setCancelled(true);
        handlePortalTrigger(player, portalType, loc, null, null);
    }

    private PortalRepository.DbPortal findWorldPortal(Block clicked) {
        if (plugin.getWorldSpawnManager() == null || clicked == null || clicked.getWorld() == null) {
            return null;
        }

        for (PortalRepository.DbPortal portal : plugin.getWorldSpawnManager().getActiveWorldPortals().values()) {
            if (!portal.worldName.equalsIgnoreCase(clicked.getWorld().getName())) {
                continue;
            }

            int cx = (int) portal.x;
            int cy = (int) portal.y;
            int cz = (int) portal.z;
            boolean clickedBeacon = clicked.getType() == Material.BEACON
                    && clicked.getX() == cx
                    && clicked.getY() == cy + 1
                    && clicked.getZ() == cz;
            boolean clickedGlass = clicked.getType().name().endsWith("_STAINED_GLASS")
                    && clicked.getX() == cx
                    && clicked.getY() == cy + 2
                    && clicked.getZ() == cz;

            if (clickedBeacon || clickedGlass) {
                return portal;
            }
        }

        return null;
    }

    private boolean isWorldPortalStructureBlock(Block clicked) {
        if (plugin.getWorldSpawnManager() == null || clicked == null || clicked.getWorld() == null) {
            return false;
        }

        if (clicked.getType() != Material.IRON_BLOCK
                && clicked.getType() != Material.BEACON
                && !clicked.getType().name().endsWith("_STAINED_GLASS")) {
            return false;
        }

        for (PortalRepository.DbPortal portal : plugin.getWorldSpawnManager().getActiveWorldPortals().values()) {
            if (!portal.worldName.equalsIgnoreCase(clicked.getWorld().getName())) {
                continue;
            }

            int cx = (int) portal.x;
            int cy = (int) portal.y;
            int cz = (int) portal.z;
            boolean clickedBase = clicked.getType() == Material.IRON_BLOCK
                    && clicked.getY() == cy
                    && Math.abs(clicked.getX() - cx) <= 1
                    && Math.abs(clicked.getZ() - cz) <= 1;
            boolean clickedBeacon = clicked.getType() == Material.BEACON
                    && clicked.getX() == cx
                    && clicked.getY() == cy + 1
                    && clicked.getZ() == cz;
            boolean clickedGlass = clicked.getType().name().endsWith("_STAINED_GLASS")
                    && clicked.getX() == cx
                    && clicked.getY() == cy + 2
                    && clicked.getZ() == cz;

            if (clickedBase || clickedBeacon || clickedGlass) {
                return true;
            }
        }

        return false;
    }

    private boolean isPartyLeader(Player player) {
        try {
            net.Indyuce.mmocore.api.player.PlayerData data = net.Indyuce.mmocore.api.player.PlayerData.get(player.getUniqueId());
            if (data != null && data.getParty() != null) {
                net.Indyuce.mmocore.party.provided.Party party = (net.Indyuce.mmocore.party.provided.Party) data.getParty();
                if (party != null) {
                    return party.getOwner().getUniqueId().equals(player.getUniqueId());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[LF] External MMOCore API error: " + e.getMessage());
        }
        return true; // Solo fallback
    }

    private List<UUID> getPartyMembers(Player player) {
        try {
            net.Indyuce.mmocore.api.player.PlayerData data = net.Indyuce.mmocore.api.player.PlayerData.get(player.getUniqueId());
            if (data != null && data.getParty() != null) {
                net.Indyuce.mmocore.party.provided.Party party = (net.Indyuce.mmocore.party.provided.Party) data.getParty();
                if (party != null) {
                    List<UUID> uuids = new ArrayList<>();
                    for (net.Indyuce.mmocore.api.player.PlayerData member : party.getOnlineMembers()) {
                        uuids.add(member.getUniqueId());
                    }
                    return uuids;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[LF] External MMOCore API error: " + e.getMessage());
        }
        return Collections.singletonList(player.getUniqueId()); // Solo fallback
    }
}
