package me.lovelyfrontier.listener;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class ChestProtectionListener implements Listener {

    private final LovelyFrontierPlugin plugin;
    private final NamespacedKey lootWandKey;

    public ChestProtectionListener(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
        this.lootWandKey = new NamespacedKey(plugin, "loot_wand");
    }

    private boolean isPortalBlock(Block block) {
        if (plugin.getWorldSpawnManager() == null) return false;
        for (me.lovelyfrontier.repository.PortalRepository.DbPortal portal : plugin.getWorldSpawnManager().getActiveWorldPortals().values()) {
            if (block.getWorld().getName().equals(portal.worldName)) {
                int bx = block.getX();
                int by = block.getY();
                int bz = block.getZ();
                int px = (int) portal.x;
                int py = (int) portal.y;
                int pz = (int) portal.z;

                // Check 3x3 base layer (IRON_BLOCKs) at py
                if (by == py) {
                    if (Math.abs(bx - px) <= 1 && Math.abs(bz - pz) <= 1) {
                        return true;
                    }
                }
                // Check Beacon block at py + 1
                else if (by == py + 1) {
                    if (bx == px && bz == pz) {
                        return true;
                    }
                }
                // Check Glass block at py + 2
                else if (by == py + 2) {
                    if (bx == px && bz == pz) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Prevent breaking chests inside active dungeon instances, and prevent breaking world portal blocks.
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // 1. Prevent chest breaking in dungeon instances
        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            String worldName = block.getWorld().getName();
            if (worldName.startsWith("lf_instance_")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(MessageUtil.get("no_permission"));
                return;
            }
        }

        // 2. Prevent breaking portal blocks (Beacon portal zone)
        if (isPortalBlock(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.get("no_permission"));
        }
    }

    @EventHandler
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        event.blockList().removeIf(this::isPortalBlock);
    }

    @EventHandler
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        event.blockList().removeIf(this::isPortalBlock);
    }

    /**
     * Intercept interaction with the Loot Wand to tag chests.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        if (!item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(lootWandKey, PersistentDataType.BYTE)) {
            return;
        }

        // It is the Loot Wand! Cancel interaction
        event.setCancelled(true);

        if (!player.hasPermission("lf.admin.wand")) {
            player.sendMessage(MessageUtil.get("no_permission"));
            return;
        }

        // Open the Loot Tagging GUI for this chest
        // We will implement this GUI in me.lovelyfrontier.gui.admin.LootEditorGUI
        // For now, let's open it
        me.lovelyfrontier.gui.admin.LootEditorGUI.openTaggingGUI(player, block);
    }
}
