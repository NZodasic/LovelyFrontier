package me.lovelyfrontier.gui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import me.lovelyfrontier.LovelyFrontierPlugin;
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

public class TicketGUI {

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

        // Load owned tickets from database (Async)
        plugin.getTicketRepository().getAllTickets(player.getUniqueId()).thenAccept(tickets -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                ChestGui gui = new ChestGui(3, "Vé Của Bạn");
                gui.setOnTopClick(event -> event.setCancelled(true));

                StaticPane pane = new StaticPane(0, 0, 9, 3);
                int slot = 0;
                Set<String> filledSlots = new HashSet<>();

                for (Map.Entry<String, Integer> entry : tickets.entrySet()) {
                    if (entry.getValue() <= 0) continue;

                    String[] split = entry.getKey().split(":");
                    String dungeonId = split[0];
                    String difficulty = split.length > 1 ? split[1] : "NORMAL";

                    ItemStack ticket = new ItemStack(Material.PAPER);
                    ItemMeta meta = ticket.getItemMeta();
                    if (meta != null) {
                        if (dungeonId.equalsIgnoreCase("UNIVERSAL")) {
                            meta.setDisplayName("§d§lVé Phụ Bản Vạn Năng");
                            List<String> lore = new ArrayList<>();
                            lore.add("§7Truy cập bất kỳ phụ bản nào với mọi độ khó.");
                            lore.add("");
                            lore.add("§7Số lượng: §b" + entry.getValue());
                            meta.setLore(lore);
                        } else {
                            String name = plugin.getDungeonManager().getDungeon(dungeonId) != null
                                     ? plugin.getDungeonManager().getDungeon(dungeonId).getName()
                                     : dungeonId;
                            meta.setDisplayName("§e§lVé " + name);
                            List<String> lore = new ArrayList<>();
                            lore.add("§7Độ khó: §b" + difficulty);
                            lore.add("");
                            lore.add("§7Số lượng: §b" + entry.getValue());
                            meta.setLore(lore);
                        }
                        ticket.setItemMeta(meta);
                    }

                    pane.addItem(new GuiItem(ticket, event -> {
                        if (!checkDebounce(player.getUniqueId())) return;
                        // No action on click, just displaying info
                    }), slot % 9, slot / 9);
                    filledSlots.add((slot % 9) + "," + (slot / 9));

                    slot++;
                    if (slot >= 27) break;
                }

                if (slot == 0) {
                    // Show a "no tickets" display item
                    ItemStack empty = new ItemStack(Material.BARRIER);
                    ItemMeta emptyMeta = empty.getItemMeta();
                    if (emptyMeta != null) {
                        emptyMeta.setDisplayName("§cBạn Không Sở Hữu Vé Nào");
                        List<String> lore = new ArrayList<>();
                        lore.add("§7Mua vé bằng lệnh §a/lf shop§7.");
                        emptyMeta.setLore(lore);
                        empty.setItemMeta(emptyMeta);
                    }
                    pane.addItem(new GuiItem(empty, event -> {}), 4, 1);
                    filledSlots.add("4,1");
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
    }
}
