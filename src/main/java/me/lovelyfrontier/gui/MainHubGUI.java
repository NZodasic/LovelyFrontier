package me.lovelyfrontier.gui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import me.lovelyfrontier.LovelyFrontierPlugin;
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

public class MainHubGUI {

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
        ChestGui gui = new ChestGui(3, "LovelyFrontier - Trung Tâm");
        gui.setOnTopClick(event -> event.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);

        // 1. Enter Dungeons Button
        ItemStack enterDungeon = new ItemStack(Material.COMPASS);
        ItemMeta dMeta = enterDungeon.getItemMeta();
        if (dMeta != null) {
            dMeta.setDisplayName("§a§lVào Phụ Bản");
            List<String> lore = new ArrayList<>();
            lore.add("§7Thử thách bản thân trong phụ bản");
            lore.add("§7tiêu diệt Boss và nhận trang bị huyền thoại.");
            lore.add("");
            lore.add("§e▶ Nhấp để xem danh sách");
            dMeta.setLore(lore);
            enterDungeon.setItemMeta(dMeta);
        }
        pane.addItem(new GuiItem(enterDungeon, event -> {
            if (!checkDebounce(player.getUniqueId())) return;
            player.closeInventory();
            DungeonListGUI.open(player, null);
        }), 2, 1);

        // 2. Ticket Shop Button
        ItemStack ticketShop = new ItemStack(Material.GOLD_INGOT);
        ItemMeta sMeta = ticketShop.getItemMeta();
        if (sMeta != null) {
            sMeta.setDisplayName("§6§lCửa Hàng Vé");
            List<String> lore = new ArrayList<>();
            lore.add("§7Mua vé tham gia phụ bản");
            lore.add("§7bằng số dư tài khoản của bạn.");
            lore.add("");
            lore.add("§e▶ Nhấp để mở cửa hàng");
            sMeta.setLore(lore);
            ticketShop.setItemMeta(sMeta);
        }
        pane.addItem(new GuiItem(ticketShop, event -> {
            if (!checkDebounce(player.getUniqueId())) return;
            player.closeInventory();
            ShopGUI.open(player);
        }), 4, 1);

        // 3. View Tickets Button
        ItemStack viewTickets = new ItemStack(Material.PAPER);
        ItemMeta tMeta = viewTickets.getItemMeta();
        if (tMeta != null) {
            tMeta.setDisplayName("§d§lXem Vé Của Bạn");
            List<String> lore = new ArrayList<>();
            lore.add("§7Kiểm tra các loại vé phụ bản");
            lore.add("§7mà bạn đang sở hữu.");
            lore.add("");
            lore.add("§e▶ Nhấp để xem");
            tMeta.setLore(lore);
            viewTickets.setItemMeta(tMeta);
        }
        pane.addItem(new GuiItem(viewTickets, event -> {
            if (!checkDebounce(player.getUniqueId())) return;
            player.closeInventory();
            TicketGUI.open(player);
        }), 6, 1);

        // Fill background
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        if (bgMeta != null) {
            bgMeta.setDisplayName(" ");
            bg.setItemMeta(bgMeta);
        }
        GuiItem bgItem = new GuiItem(bg, event -> event.setCancelled(true));
        Set<String> filledSlots = new HashSet<>(java.util.Arrays.asList("2,1", "4,1", "6,1"));
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
