package me.lovelyfrontier.gui.admin;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.DungeonConfig;
import me.lovelyfrontier.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LootEditorGUI {

    public static void openTaggingGUI(Player player, Block chestBlock) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();
        String worldName = chestBlock.getWorld().getName();

        // 1. Resolve dungeon ID. Check if world name matches a dungeon
        DungeonConfig config = plugin.getDungeonManager().getDungeon(worldName);
        if (config != null) {
            openLootPoolSelector(player, config.getId(), chestBlock);
        } else {
            // World name does not match, let admin select which dungeon they are editing
            ChestGui gui = new ChestGui(3, "Chọn Phụ Bản Mục Tiêu");
            gui.setOnTopClick(event -> event.setCancelled(true));
            StaticPane pane = new StaticPane(0, 0, 9, 3);
            int slot = 0;
            Set<String> filledSlots = new HashSet<>();

            for (DungeonConfig dc : plugin.getDungeonManager().getAllDungeons()) {
                ItemStack item = new ItemStack(Material.BOOK);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§b" + dc.getName());
                    List<String> lore = new ArrayList<>();
                    lore.add("§7Gắn thẻ tọa độ rương trong phụ bản này.");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }

                int cx = slot % 9;
                int cy = slot / 9;
                pane.addItem(new GuiItem(item, event -> {
                    player.closeInventory();
                    openLootPoolSelector(player, dc.getId(), chestBlock);
                }), cx, cy);
                filledSlots.add(cx + "," + cy);
                slot++;
            }

            // Fill background
            ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta bgMeta = bg.getItemMeta();
            if (bgMeta != null) bgMeta.setDisplayName(" ");
            bg.setItemMeta(bgMeta);
            GuiItem bgItem = new GuiItem(bg, event -> {});
            for (int x = 0; x < 9; x++) {
                for (int y = 0; y < 3; y++) {
                    if (!filledSlots.contains(x + "," + y)) pane.addItem(bgItem, x, y);
                }
            }

            gui.addPane(pane);
            gui.show(player);
        }
    }

    private static void openLootPoolSelector(Player player, String dungeonId, Block chestBlock) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();
        ChestGui gui = new ChestGui(3, "Chọn Kho Báu (Loot Pool)");
        gui.setOnTopClick(event -> event.setCancelled(true));
        StaticPane pane = new StaticPane(0, 0, 9, 3);
        int slot = 0;
        Set<String> filledSlots = new HashSet<>();

        // Fetch loaded pools
        for (String poolId : plugin.getLootManager().getLootPool("common_chest") != null ? List.of("common_chest", "rare_chest") : List.of("common_chest")) {
            ItemStack item = new ItemStack(poolId.contains("rare") ? Material.GOLDEN_CHESTPLATE : Material.LEATHER_CHESTPLATE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e§l" + poolId.replace("_", " ").toUpperCase());
                List<String> lore = new ArrayList<>();
                lore.add("§7Nhấp để gắn thẻ rương với kho báu này.");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            int cx = slot % 9;
            int cy = slot / 9;
            pane.addItem(new GuiItem(item, event -> {
                player.closeInventory();
                
                int x = chestBlock.getX();
                int y = chestBlock.getY();
                int z = chestBlock.getZ();
                
                plugin.getLootManager().tagChest(dungeonId, chestBlock.getWorld().getName(), x, y, z, poolId)
                        .thenAccept(success -> {
                            if (success) {
                                player.sendMessage(MessageUtil.get("admin_chest_tagged", "pool", poolId));
                            } else {
                                player.sendMessage("§cThất bại khi lưu rương đã gắn thẻ.");
                            }
                        });
            }), cx, cy);
            filledSlots.add(cx + "," + cy);
            slot++;
        }

        // Fill background
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        if (bgMeta != null) bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        GuiItem bgItem = new GuiItem(bg, event -> {});
        for (int cx = 0; cx < 9; cx++) {
            for (int cy = 0; cy < 3; cy++) {
                if (!filledSlots.contains(cx + "," + cy)) pane.addItem(bgItem, cx, cy);
            }
        }

        gui.addPane(pane);
        gui.show(player);
    }
}
