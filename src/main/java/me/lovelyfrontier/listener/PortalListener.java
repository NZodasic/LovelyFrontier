package me.lovelyfrontier.listener;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.PortalType;
import me.lovelyfrontier.repository.PortalRepository;
import me.lovelyfrontier.util.MessageUtil;
import me.lovelyfrontier.gui.DifficultyGUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class PortalListener implements Listener {

    private final LovelyFrontierPlugin plugin;

    public PortalListener(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
    }

    private void handlePortalTrigger(Player player, PortalType portalType, Location centerLoc, String matchedWorldPortalId, String matchedWorldPortalDungeonId, Runnable cancelEvent) {
        if (cancelEvent != null) {
            cancelEvent.run();
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

        // Layer 2 Lock (Database - Async)
        plugin.getPortalRepository().tryAcquireLock(portalId, player.getUniqueId()).thenAccept(dbLocked -> {
            if (!dbLocked) {
                // Rollback Layer 1
                plugin.getInMemoryPortalLock().unlock(portalId);
                player.sendMessage(MessageUtil.get("portal_locked"));
                return;
            }

            // Both locks acquired successfully, open DifficultyGUI (back on main thread)
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (matchedWorldPortalDungeonId != null) {
                    DifficultyGUI.openForDungeon(player, matchedWorldPortalDungeonId, portalId);
                } else {
                    DifficultyGUI.open(player, portalType, portalId);
                }
            });
        }).exceptionally(ex -> {
            plugin.getInMemoryPortalLock().unlock(portalId);
            plugin.getLogger().log(Level.SEVERE, "[LF] Database lock failed", ex);
            return null;
        });
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

        // 2. Get drop location and read 3x3 grid around drop location (must be on main thread)
        Location loc = player.getLocation().subtract(0, 1, 0);
        World world = loc.getWorld();
        if (world == null) return;

        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();

        // Check if it matches an active World Spawn Portal (within 5 blocks of beacon)
        PortalRepository.DbPortal matchedWorldPortal = null;
        if (plugin.getWorldSpawnManager() != null) {
            for (PortalRepository.DbPortal wp : plugin.getWorldSpawnManager().getActiveWorldPortals().values()) {
                if (wp.worldName.equalsIgnoreCase(world.getName())) {
                    double dx = wp.x - cx;
                    double dy = wp.y - cy;
                    double dz = wp.z - cz;
                    if (dx * dx + dy * dy + dz * dz <= 25.0) {
                        matchedWorldPortal = wp;
                        break;
                    }
                }
            }
        }

        if (matchedWorldPortal != null) {
            String dungeonId = matchedWorldPortal.dungeonId;
            PortalType matchedType = null;
            for (PortalType pt : plugin.getPortalManager().getPortalTypes().values()) {
                if (pt.getAction().equalsIgnoreCase("SPECIFIC:" + dungeonId) ||
                    pt.getAction().equalsIgnoreCase("RANDOM_DUNGEON") ||
                    pt.getAction().equalsIgnoreCase("OPEN_MENU")) {
                    if (pt.getTriggerItem() == droppedType && (pt.getTriggerMethod() == null || pt.getTriggerMethod().equalsIgnoreCase("DROP"))) {
                        matchedType = pt;
                        break;
                    }
                }
            }

            if (matchedType == null && droppedType == Material.ENDER_EYE) {
                matchedType = plugin.getPortalManager().getPortalTypes().get("standard_portal");
            }

            if (matchedType != null) {
                final PortalRepository.DbPortal finalWorldPortal = matchedWorldPortal;
                final PortalType finalMatchedType = matchedType;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    handlePortalTrigger(player, finalMatchedType, loc, finalWorldPortal.portalId, dungeonId, () -> event.setCancelled(true));
                });
                return;
            }
        }

        java.util.Map<String, Material> blockCache = new java.util.HashMap<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                blockCache.put(dx + "," + dz, world.getBlockAt(cx + dx, cy, cz + dz).getType());
            }
        }

        // 3. Compare against registered portal types (async)
        plugin.getPortalManager().detectPattern(blockCache).thenAccept(portalType -> {
            if (portalType == null || (portalType.getTriggerMethod() != null && !portalType.getTriggerMethod().equalsIgnoreCase("DROP"))) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                handlePortalTrigger(player, portalType, loc, null, null, () -> event.setCancelled(true));
            });
        });
    }

    @EventHandler
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        org.bukkit.inventory.ItemStack handItem = player.getInventory().getItemInMainHand();
        Material itemType = handItem.getType();
        if (itemType == Material.AIR) return;

        // Determine if this is a right click or left click action
        String method;
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            method = "RIGHT_CLICK";
        } else if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            method = "LEFT_CLICK";
        } else {
            return;
        }

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

        // Check if it matches an active World Spawn Portal (within 5 blocks of beacon)
        PortalRepository.DbPortal matchedWorldPortal = null;
        if (plugin.getWorldSpawnManager() != null) {
            for (PortalRepository.DbPortal wp : plugin.getWorldSpawnManager().getActiveWorldPortals().values()) {
                if (wp.worldName.equalsIgnoreCase(world.getName())) {
                    double dx = wp.x - cx;
                    double dy = wp.y - cy;
                    double dz = wp.z - cz;
                    if (dx * dx + dy * dy + dz * dz <= 25.0) {
                        matchedWorldPortal = wp;
                        break;
                    }
                }
            }
        }

        if (matchedWorldPortal != null) {
            String dungeonId = matchedWorldPortal.dungeonId;
            PortalType matchedType = null;
            for (PortalType pt : plugin.getPortalManager().getPortalTypes().values()) {
                if (pt.getAction().equalsIgnoreCase("SPECIFIC:" + dungeonId) ||
                    pt.getAction().equalsIgnoreCase("RANDOM_DUNGEON") ||
                    pt.getAction().equalsIgnoreCase("OPEN_MENU")) {
                    if (pt.getTriggerItem() == itemType && pt.getTriggerMethod() != null && pt.getTriggerMethod().equalsIgnoreCase(method)) {
                        matchedType = pt;
                        break;
                    }
                }
            }
            if (matchedType != null) {
                final PortalRepository.DbPortal finalWorldPortal = matchedWorldPortal;
                final PortalType finalMatchedType = matchedType;
                handlePortalTrigger(player, finalMatchedType, loc, finalWorldPortal.portalId, dungeonId, () -> event.setCancelled(true));
                return;
            }
        }

        java.util.Map<String, Material> blockCache = new java.util.HashMap<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                blockCache.put(dx + "," + dz, world.getBlockAt(cx + dx, cy, cz + dz).getType());
            }
        }

        // Compare against registered portal types (async)
        plugin.getPortalManager().detectPattern(blockCache).thenAccept(portalType -> {
            if (portalType == null || portalType.getTriggerMethod() == null || !portalType.getTriggerMethod().equalsIgnoreCase(method)) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                handlePortalTrigger(player, portalType, loc, null, null, () -> event.setCancelled(true));
            });
        });
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
