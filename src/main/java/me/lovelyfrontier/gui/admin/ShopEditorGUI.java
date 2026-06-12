package me.lovelyfrontier.gui.admin;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import me.lovelyfrontier.LovelyFrontierPlugin;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ShopEditorGUI {

    private static double tempBasePrice;
    private static final Map<String, Double> tempMultipliers = new HashMap<>();

    public static void open(Player player) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();

        // Load current config values as starting temp values
        tempBasePrice = plugin.getConfigManager().getShopBasePrice();
        tempMultipliers.clear();
        for (String diff : Arrays.asList("VERY_EASY", "EASY", "NORMAL", "HARD", "VERY_HARD")) {
            tempMultipliers.put(diff, plugin.getConfigManager().getShopMultiplier(diff));
        }

        openInternal(player);
    }

    private static void openInternal(Player player) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();

        ChestGui gui = new ChestGui(3, "Cấu Hình Cửa Hàng Vé");
        gui.setOnTopClick(event -> event.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        Set<String> filledSlots = new HashSet<>();

        // 1. Base Price (Gold Block)
        ItemStack basePriceItem = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta basePriceMeta = basePriceItem.getItemMeta();
        if (basePriceMeta != null) {
            basePriceMeta.setDisplayName("§e§lGiá Vé Cơ Bản");
            basePriceMeta.setLore(Arrays.asList(
                    "§7Giá vé cơ bản hiện tại: §a$" + String.format(Locale.US, "%.0f", tempBasePrice),
                    "",
                    "§e◀ Click trái: +$100",
                    "§e◀ Click phải: -$100",
                    "§b◀ Shift + Click trái: +$1000",
                    "§b◀ Shift + Click phải: -$1000"
            ));
            basePriceItem.setItemMeta(basePriceMeta);
        }
        pane.addItem(new GuiItem(basePriceItem, event -> {
            double diff = 100.0;
            if (event.isShiftClick()) {
                diff = 1000.0;
            }
            if (event.isLeftClick()) {
                tempBasePrice += diff;
            } else if (event.isRightClick()) {
                tempBasePrice = Math.max(0, tempBasePrice - diff);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            openInternal(player); // Refresh GUI
        }), 1, 1);
        filledSlots.add("1,1");

        // Difficulties
        String[] difficulties = {"VERY_EASY", "EASY", "NORMAL", "HARD", "VERY_HARD"};
        Material[] materials = {Material.LIME_DYE, Material.GREEN_DYE, Material.YELLOW_DYE, Material.ORANGE_DYE, Material.RED_DYE};
        String[] diffNames = {"§aRất Dễ (VERY_EASY)", "§2Dễ (EASY)", "§eThường (NORMAL)", "§6Khó (HARD)", "§cRất Khó (VERY_HARD)"};
        int[] slots = {3, 4, 5, 6, 7};

        for (int i = 0; i < 5; i++) {
            final String diff = difficulties[i];
            final String diffName = diffNames[i];
            final double mult = tempMultipliers.getOrDefault(diff, 1.0);
            Material mat = materials[i];

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(diffName);
                meta.setLore(Arrays.asList(
                        "§7Hệ số nhân hiện tại: §f" + String.format(Locale.US, "%.2f", mult),
                        "§7Giá vé thực tế: §a$" + String.format(Locale.US, "%.0f", tempBasePrice * mult),
                        "",
                        "§e◀ Click trái: +0.05",
                        "§e◀ Click phải: -0.05",
                        "§b◀ Shift + Click trái: +0.25",
                        "§b◀ Shift + Click phải: -0.25"
                ));
                item.setItemMeta(meta);
            }

            pane.addItem(new GuiItem(item, event -> {
                double increment = 0.05;
                if (event.isShiftClick()) {
                    increment = 0.25;
                }
                double currentMult = tempMultipliers.getOrDefault(diff, 1.0);
                if (event.isLeftClick()) {
                    currentMult += increment;
                } else if (event.isRightClick()) {
                    currentMult = Math.max(0.0, currentMult - increment);
                }
                tempMultipliers.put(diff, currentMult);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                openInternal(player); // Refresh GUI
            }), slots[i], 1);
            filledSlots.add(slots[i] + ",1");
        }

        // Save Button (Emerald Block) at Slot 4,2
        ItemStack saveItem = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta saveMeta = saveItem.getItemMeta();
        if (saveMeta != null) {
            saveMeta.setDisplayName("§a§lLưu Cấu Hình");
            saveMeta.setLore(Arrays.asList(
                    "§7Lưu toàn bộ thay đổi giá vé vào config.yml",
                    "§7và áp dụng lập tức.",
                    "",
                    "§e▶ Nhấp để lưu"
            ));
            saveItem.setItemMeta(saveMeta);
        }
        pane.addItem(new GuiItem(saveItem, event -> {
            plugin.getConfigManager().saveShopConfig(tempBasePrice, tempMultipliers);
            player.sendMessage("§a[LovelyFrontier] Đã lưu và áp dụng cấu hình giá vé cửa hàng!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            player.closeInventory();
            AdminMainGUI.open(player);
        }), 4, 2);
        filledSlots.add("4,2");

        // Back Button (Barrier) at Slot 8,2
        ItemStack backItem = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§lQuay Lại");
            backMeta.setLore(Collections.singletonList("§7Quay lại giao diện Admin Panel"));
            backItem.setItemMeta(backMeta);
        }
        pane.addItem(new GuiItem(backItem, event -> {
            player.closeInventory();
            AdminMainGUI.open(player);
        }), 8, 2);
        filledSlots.add("8,2");

        // Background Glass
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        if (bgMeta != null) bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        GuiItem bgItem = new GuiItem(bg, event -> {});
        for (int cx = 0; cx < 9; cx++) {
            for (int cy = 0; cy < 3; cy++) {
                if (!filledSlots.contains(cx + "," + cy)) {
                    pane.addItem(bgItem, cx, cy);
                }
            }
        }

        gui.addPane(pane);
        gui.show(player);
    }
}
