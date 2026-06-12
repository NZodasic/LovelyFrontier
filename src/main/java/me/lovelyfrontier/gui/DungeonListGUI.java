package me.lovelyfrontier.gui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.DungeonConfig;
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

public class DungeonListGUI {

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

    public static void open(Player player, String portalId) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();
        ChestGui gui = new ChestGui(4, "Chọn Phụ Bản");
        gui.setOnTopClick(event -> event.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 4);
        int slot = 0;
        Set<String> filledSlots = new HashSet<>();

        for (DungeonConfig config : plugin.getDungeonManager().getAllDungeons()) {
            ItemStack item = new ItemStack(Material.STONE_BRICKS);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§6§l" + config.getName());
                List<String> lore = new ArrayList<>();
                lore.add("§7Thành viên tối thiểu: §e" + config.getMinPartySize());
                lore.add("§7Giới hạn thời gian: §e" + (config.getTimeLimitSeconds() / 60) + " phút");
                lore.add("");
                lore.add("§a▶ Nhấp để chọn độ khó");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            int cx = slot % 9;
            int cy = slot / 9;
            pane.addItem(new GuiItem(item, event -> {
                if (!checkDebounce(player.getUniqueId())) return;
                player.closeInventory();
                DifficultyGUI.openForDungeon(player, config.getId(), portalId);
            }), cx, cy);
            filledSlots.add(cx + "," + cy);

            slot++;
            if (slot >= 27) break; // keep slot range
        }

        // Fill background
        ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = background.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            background.setItemMeta(meta);
        }
        GuiItem bgItem = new GuiItem(background, event -> event.setCancelled(true));
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 4; y++) {
                if (!filledSlots.contains(x + "," + y)) {
                    pane.addItem(bgItem, x, y);
                }
            }
        }

        gui.addPane(pane);
        gui.show(player);
    }
}
