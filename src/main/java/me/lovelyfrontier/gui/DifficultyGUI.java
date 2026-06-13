package me.lovelyfrontier.gui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.PlayerSession;
import me.lovelyfrontier.model.PortalType;
import me.lovelyfrontier.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DifficultyGUI {

    private static final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    /**
     * Checks the GUI action debounce for the player.
     * Returns true if allowed, false if debounced.
     */
    private static boolean checkDebounce(UUID uuid) {
        long now = System.currentTimeMillis();
        long last = lastClick.getOrDefault(uuid, 0L);
        if (now - last < 200) {
            return false;
        }
        lastClick.put(uuid, now);
        return true;
    }

    public static void open(Player player, PortalType portalType, String portalId) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();
        String action = portalType.getAction();
        
        // Resolve dungeon ID
        String dungeonId;
        if (action.startsWith("SPECIFIC:")) {
            dungeonId = action.substring("SPECIFIC:".length());
        } else if (action.equalsIgnoreCase("RANDOM_DUNGEON")) {
            List<String> dungeons = new ArrayList<>();
            plugin.getDungeonManager().getAllDungeons().forEach(d -> dungeons.add(d.getId()));
            if (dungeons.isEmpty()) {
                player.sendMessage("§cKhông có phụ bản khả dụng.");
                releasePortalLock(plugin, portalId);
                return;
            }
            dungeonId = dungeons.get(new Random().nextInt(dungeons.size()));
        } else {
            // OPEN_MENU: open dungeon selection menu first
            DungeonListGUI.open(player, portalId);
            return;
        }

        openForDungeon(player, dungeonId, portalId);
    }

    public static void openForDungeon(Player player, String dungeonId, String portalId) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();

        plugin.getPlayerProfileRepository().getPlaytime(player.getUniqueId()).thenAccept(playtime -> {
            double sessionHours = me.lovelyfrontier.listener.PlayerConnectionListener.getOnlineSessionHours(player.getUniqueId());
            double totalPlaytime = playtime + sessionHours;
            double reqHours = plugin.getConfigManager().getAntiAbuseMinPlaytimeHours();
            if (totalPlaytime < reqHours) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String requiredStr = plugin.getConfigManager().getAntiAbuseMinPlaytime();
                    String currentStr = me.lovelyfrontier.ConfigManager.formatPlaytime(totalPlaytime);
                    player.sendMessage(MessageUtil.get("not_enough_playtime", "hours", requiredStr, "required", requiredStr, "current", currentStr));
                    releasePortalLock(plugin, portalId);
                });
                return;
            }

            // Fetch ticket status for each difficulty async (Rule R-001)
            CompletableFuture<String> tVE = plugin.getTicketRepository().findValidTicket(player.getUniqueId(), dungeonId, "VERY_EASY");
            CompletableFuture<String> tE = plugin.getTicketRepository().findValidTicket(player.getUniqueId(), dungeonId, "EASY");
            CompletableFuture<String> tN = plugin.getTicketRepository().findValidTicket(player.getUniqueId(), dungeonId, "NORMAL");
            CompletableFuture<String> tH = plugin.getTicketRepository().findValidTicket(player.getUniqueId(), dungeonId, "HARD");
            CompletableFuture<String> tVH = plugin.getTicketRepository().findValidTicket(player.getUniqueId(), dungeonId, "VERY_HARD");

            final String finalDungeonId = dungeonId;
            CompletableFuture.allOf(tVE, tE, tN, tH, tVH).thenAccept(v -> {
                boolean hasVE = tVE.join() != null;
                boolean hasE = tE.join() != null;
                boolean hasN = tN.join() != null;
                boolean hasH = tH.join() != null;
                boolean hasVH = tVH.join() != null;

                // Open inventory GUI on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    ChestGui gui = new ChestGui(3, "Chọn Độ Khó");
                    gui.setOnTopClick(event -> event.setCancelled(true));

                    StaticPane pane = new StaticPane(0, 0, 9, 3);

                    // Add difficulty buttons
                    pane.addItem(createDifficultyItem("VERY_EASY", Material.LIME_DYE, hasVE, "0.8x", "0.6x", "0.6x", player, finalDungeonId, portalId), 1, 1);
                    pane.addItem(createDifficultyItem("EASY", Material.GREEN_DYE, hasE, "1.0x", "1.0x", "1.0x", player, finalDungeonId, portalId), 2, 1);
                    pane.addItem(createDifficultyItem("NORMAL", Material.YELLOW_DYE, hasN, "1.3x", "1.5x", "1.3x", player, finalDungeonId, portalId), 4, 1);
                    pane.addItem(createDifficultyItem("HARD", Material.ORANGE_DYE, hasH, "1.7x", "2.0x", "1.6x", player, finalDungeonId, portalId), 6, 1);
                    pane.addItem(createDifficultyItem("VERY_HARD", Material.RED_DYE, hasVH, "2.5x", "3.0x", "2.0x", player, finalDungeonId, portalId), 7, 1);

                    // Fill background
                    ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                    ItemMeta meta = background.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(" ");
                        background.setItemMeta(meta);
                    }
                    GuiItem bgItem = new GuiItem(background, event -> event.setCancelled(true));
                    Set<String> filledSlots = new HashSet<>(Arrays.asList("1,1", "2,1", "4,1", "6,1", "7,1"));
                    for (int x = 0; x < 9; x++) {
                        for (int y = 0; y < 3; y++) {
                            if (!filledSlots.contains(x + "," + y)) {
                                pane.addItem(bgItem, x, y);
                            }
                        }
                    }

                    gui.addPane(pane);
                    gui.show(player);
                });
            });
        });
    }

    private static GuiItem createDifficultyItem(String difficulty, Material material, boolean hasTicket,
                                                String loot, String hp, String dmg, Player leader,
                                                String dungeonId, String portalId) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayDifficulty = difficulty;
            if (difficulty.equalsIgnoreCase("VERY_EASY")) displayDifficulty = "RẤT DỄ";
            else if (difficulty.equalsIgnoreCase("EASY")) displayDifficulty = "DỄ";
            else if (difficulty.equalsIgnoreCase("NORMAL")) displayDifficulty = "THƯỜNG";
            else if (difficulty.equalsIgnoreCase("HARD")) displayDifficulty = "KHÓ";
            else if (difficulty.equalsIgnoreCase("VERY_HARD")) displayDifficulty = "RẤT KHÓ";
            
            meta.setDisplayName("§e§l" + displayDifficulty);
            List<String> lore = new ArrayList<>();
            lore.add("§7Phần thưởng rơi ra: §a" + loot);
            lore.add("§7Tỉ lệ máu quái: §c" + hp);
            lore.add("§7Tỉ lệ sát thương quái: §c" + dmg);
            lore.add("");
            lore.add(hasTicket ? "§a✔ Sẵn sàng sử dụng vé" : "§c✖ Không có vé");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return new GuiItem(item, event -> {
            if (!checkDebounce(leader.getUniqueId())) return;
            leader.closeInventory();

            if (!hasTicket) {
                leader.sendMessage(MessageUtil.get("no_tickets"));
                releasePortalLock(plugin, portalId);
                return;
            }

            // Resolve party members using MMOCore API
            List<UUID> partyMembers = new ArrayList<>();
            try {
                net.Indyuce.mmocore.api.player.PlayerData data = net.Indyuce.mmocore.api.player.PlayerData.get(leader.getUniqueId());
                if (data != null && data.getParty() != null) {
                    net.Indyuce.mmocore.party.provided.Party party = (net.Indyuce.mmocore.party.provided.Party) data.getParty();
                    if (party != null) {
                        for (net.Indyuce.mmocore.api.player.PlayerData member : party.getOnlineMembers()) {
                            partyMembers.add(member.getUniqueId());
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error fetching party members from MMOCore: " + e.getMessage());
            }

            if (partyMembers.isEmpty()) {
                partyMembers.add(leader.getUniqueId());
            }

            // Create entry session
            plugin.getSessionManager().createSession(dungeonId, difficulty, leader.getUniqueId(), partyMembers, portalId);
        });
    }

    private static void releasePortalLock(LovelyFrontierPlugin plugin, String portalId) {
        if (portalId == null) {
            return;
        }

        plugin.getInMemoryPortalLock().unlock(portalId);
        if (plugin.getWorldSpawnManager() != null
                && plugin.getWorldSpawnManager().getActiveWorldPortals().containsKey(portalId)) {
            plugin.getPortalRepository().releaseLock(portalId);
        }
    }
}
