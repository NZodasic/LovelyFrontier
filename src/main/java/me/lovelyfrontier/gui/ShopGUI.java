package me.lovelyfrontier.gui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.DungeonConfig;
import me.lovelyfrontier.util.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShopGUI {

    private static final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    private static boolean checkDebounce(UUID uuid) {
        long now = System.currentTimeMillis();
        long last = lastClick.getOrDefault(uuid, 0L);
        if (now - last < 200) {
            return false;
        }
        lastClick.put(uuid, now);
        return true;
    }

    public static void open(Player player) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();

        // 1. Check playtime gate and flagged status (Async)
        plugin.getPlayerProfileRepository().getPlaytime(player.getUniqueId()).thenAccept(playtime -> {
            double sessionHours = me.lovelyfrontier.listener.PlayerConnectionListener.getOnlineSessionHours(player.getUniqueId());
            double totalPlaytime = playtime + sessionHours;
            double reqHours = plugin.getConfigManager().getAntiAbuseMinPlaytimeHours();
            if (totalPlaytime < reqHours) {
                String requiredStr = plugin.getConfigManager().getAntiAbuseMinPlaytime();
                String currentStr = me.lovelyfrontier.ConfigManager.formatPlaytime(totalPlaytime);
                player.sendMessage(MessageUtil.get("not_enough_playtime", "hours", requiredStr, "required", requiredStr, "current", currentStr));
                return;
            }

            plugin.getPlayerProfileRepository().isFlagged(player.getUniqueId()).thenAccept(flagged -> {
                if (flagged) {
                    player.sendMessage(MessageUtil.get("alt_flagged"));
                    return;
                }

                // 2. Open GUI on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    Economy economy = plugin.getEconomy();
                    if (economy == null) {
                        player.sendMessage("§cKhông tìm thấy hệ thống kinh tế.");
                        return;
                    }

                    ChestGui gui = new ChestGui(3, "Cửa Hàng Vé");
                    gui.setOnTopClick(event -> event.setCancelled(true));

                    StaticPane pane = new StaticPane(0, 0, 9, 3);
                    Set<String> filledSlots = new HashSet<>();

                    // Display tickets per dungeon (clicking opens the difficulty selector)
                    int x = 1;
                    for (DungeonConfig dc : plugin.getDungeonManager().getAllDungeons()) {
                        ItemStack ticket = new ItemStack(Material.PAPER);
                        ItemMeta meta = ticket.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName("§d§lVé Phụ Bản: " + dc.getName());
                            List<String> lore = new ArrayList<>();
                            lore.add("§7Chọn độ khó và mua vé cho phụ bản này.");
                            lore.add("");
                            lore.add("§e▶ Nhấp để chọn");
                            meta.setLore(lore);
                            ticket.setItemMeta(meta);
                        }

                        pane.addItem(new GuiItem(ticket, event -> {
                            if (!checkDebounce(player.getUniqueId())) return;
                            openDifficultySelector(player, dc);
                        }), x, 1);
                        filledSlots.add(x + ",1");

                        x += 2;
                        if (x > 7) break;
                    }

                    // Fill background
                    ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                    ItemMeta bgMeta = bg.getItemMeta();
                    if (bgMeta != null) {
                        bgMeta.setDisplayName(" ");
                        bg.setItemMeta(bgMeta);
                    }
                    GuiItem bgItem = new GuiItem(bg, event -> event.setCancelled(true));
                    for (int cx = 0; cx < 9; cx++) {
                        for (int cy = 0; cy < 3; cy++) {
                            if (!filledSlots.contains(cx + "," + cy)) {
                                pane.addItem(bgItem, cx, cy);
                            }
                        }
                    }

                    gui.addPane(pane);
                    gui.show(player);
                });
            });
        });
    }

    public static void openDifficultySelector(Player player, DungeonConfig dc) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();
        ChestGui gui = new ChestGui(3, "Mua Vé - " + dc.getName());
        gui.setOnTopClick(event -> event.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        Set<String> filledSlots = new HashSet<>();

        String[] difficulties = {"VERY_EASY", "EASY", "NORMAL", "HARD", "VERY_HARD"};
        Material[] materials = {Material.LIME_DYE, Material.GREEN_DYE, Material.YELLOW_DYE, Material.ORANGE_DYE, Material.RED_DYE};
        String[] diffNames = {"§aRất Dễ", "§2Dễ", "§eThường", "§6Khó", "§cRất Khó"};
        double basePrice = plugin.getConfigManager().getShopBasePrice();

        int[] slots = {11, 12, 13, 14, 15}; // Center row slots

        for (int i = 0; i < 5; i++) {
            final String diff = difficulties[i];
            final String diffName = diffNames[i];
            final double price = basePrice * plugin.getConfigManager().getShopMultiplier(diff);
            Material mat = materials[i];

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§l" + diffName);
                List<String> lore = new ArrayList<>();
                lore.add("§7Phụ bản: §f" + dc.getName());
                lore.add("§7Độ khó: " + diffName);
                lore.add("");
                lore.add("§7Giá: §a$" + String.format(java.util.Locale.US, "%.0f", price));
                lore.add("");
                lore.add("§e▶ Nhấp để mua");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            int slot = slots[i];
            pane.addItem(new GuiItem(item, event -> {
                if (!checkDebounce(player.getUniqueId())) return;
                buyTicket(player, dc.getId(), diff, price);
            }), slot % 9, slot / 9);
            filledSlots.add((slot % 9) + "," + (slot / 9));
        }

        // Back button at bottom right (8,2)
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§cQuay Lại");
            back.setItemMeta(backMeta);
        }
        pane.addItem(new GuiItem(back, event -> {
            if (!checkDebounce(player.getUniqueId())) return;
            open(player);
        }), 8, 2);
        filledSlots.add("8,2");

        // Fill background
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        if (bgMeta != null) {
            bgMeta.setDisplayName(" ");
            bg.setItemMeta(bgMeta);
        }
        GuiItem bgItem = new GuiItem(bg, event -> event.setCancelled(true));
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

    private static void buyTicket(Player player, String dungeonId, String difficulty, double price) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();
        Economy economy = plugin.getEconomy();

        if (economy == null) {
            player.sendMessage("§cHệ thống kinh tế không hoạt động.");
            return;
        }

        if (!economy.has(player, price)) {
            player.sendMessage(MessageUtil.get("shop_insufficient_funds", "price", price));
            return;
        }

        economy.withdrawPlayer(player, price);
        plugin.getTicketRepository().addTicket(player.getUniqueId(), dungeonId, difficulty, 1)
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage(MessageUtil.get("shop_success", "price", price));
                    } else {
                        // Refund money on DB failure
                        economy.depositPlayer(player, price);
                        player.sendMessage("§cKhông thể tạo vé. Đã hoàn tiền lại.");
                    }
                });
    }
}
