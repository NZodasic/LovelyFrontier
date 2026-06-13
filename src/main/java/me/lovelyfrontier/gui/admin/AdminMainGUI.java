package me.lovelyfrontier.gui.admin;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.DungeonConfig;
import me.lovelyfrontier.model.DungeonInstance;
import me.lovelyfrontier.model.InstanceState;
import me.lovelyfrontier.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class AdminMainGUI {

    public static void open(Player player) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();

        ChestGui gui = new ChestGui(3, "LovelyFrontier - Admin Panel");
        gui.setOnTopClick(event -> event.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        Set<String> filledSlots = new HashSet<>();

        // 1. Reload config button
        ItemStack reloadItem = new ItemStack(Material.REPEATER);
        ItemMeta reloadMeta = reloadItem.getItemMeta();
        if (reloadMeta != null) {
            reloadMeta.setDisplayName("§a§lTải Lại Cấu Hình");
            reloadMeta.setLore(Arrays.asList(
                    "§7Tải lại toàn bộ tệp cấu hình config.yml",
                    "§7và thông tin phụ bản.",
                    "",
                    "§e▶ Nhấp để tải lại"
            ));
            reloadItem.setItemMeta(reloadMeta);
        }
        pane.addItem(new GuiItem(reloadItem, event -> {
            player.closeInventory();
            plugin.getConfigManager().reload();
            plugin.getDungeonManager().reload();
            me.lovelyfrontier.util.MessageUtil.load(plugin);
            player.sendMessage("§a[LovelyFrontier] Đã tải lại tệp cấu hình config.yml, dữ liệu phụ bản và thông điệp!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }), 1, 1);
        filledSlots.add("1,1");

        // Shop Editor Button
        ItemStack shopEditItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta shopEditMeta = shopEditItem.getItemMeta();
        if (shopEditMeta != null) {
            shopEditMeta.setDisplayName("§6§lCấu Hinh Cửa Hàng Vé");
            shopEditMeta.setLore(Arrays.asList(
                    "§7Thiết lập giá vé cơ bản và hệ số",
                    "§7giá cho từng độ khó phụ bản.",
                    "",
                    "§e▶ Nhấp để cấu hình"
            ));
            shopEditItem.setItemMeta(shopEditMeta);
        }
        pane.addItem(new GuiItem(shopEditItem, event -> {
            ShopEditorGUI.open(player);
        }), 2, 1);
        filledSlots.add("2,1");

        // 2. Active instances manager
        ItemStack beaconItem = new ItemStack(Material.BEACON);
        ItemMeta beaconMeta = beaconItem.getItemMeta();
        if (beaconMeta != null) {
            beaconMeta.setDisplayName("§b§lQuản Lý Phụ Bản Đang Hoạt Động");
            beaconMeta.setLore(Arrays.asList(
                    "§7Xem danh sách các phụ bản đang hoạt động.",
                    "§7Có thể dịch chuyển theo dõi hoặc bắt buộc đóng.",
                    "",
                    "§e▶ Nhấp để quản lý"
            ));
            beaconItem.setItemMeta(beaconMeta);
        }
        pane.addItem(new GuiItem(beaconItem, event -> {
            openActiveInstancesGUI(player);
        }), 3, 1);
        filledSlots.add("3,1");

        // 3. Get Loot Wand
        ItemStack wandItem = new ItemStack(Material.BLAZE_ROD);
        ItemMeta wandMeta = wandItem.getItemMeta();
        if (wandMeta != null) {
            wandMeta.setDisplayName("§e§lLấy Gậy Loot Wand");
            wandMeta.setLore(Arrays.asList(
                    "§7Sử dụng gậy này nhấp chuột phải vào rương",
                    "§7ở thế giới phụ bản gốc để gắn thẻ rương.",
                    "",
                    "§e▶ Nhấp để lấy gậy"
            ));
            wandMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "loot_wand"), PersistentDataType.BYTE, (byte) 1);
            wandItem.setItemMeta(wandMeta);
        }
        pane.addItem(new GuiItem(wandItem, event -> {
            player.getInventory().addItem(wandItem);
            player.closeInventory();
            player.sendMessage("§a[LovelyFrontier] Đã trao Gậy Loot Wand!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        }), 5, 1);
        filledSlots.add("5,1");

        // 4. Portal Designer (CRAFTING_TABLE)
        ItemStack designerItem = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta designerMeta = designerItem.getItemMeta();
        if (designerMeta != null) {
            designerMeta.setDisplayName("§e§lThiết Kế Cổng");
            designerMeta.setLore(Arrays.asList(
                    "§7Thiết kế cấu trúc và trigger",
                    "§7cho cổng phụ bản.",
                    "",
                    "§e▶ Nhấp để thiết kế"
            ));
            designerItem.setItemMeta(designerMeta);
        }
        pane.addItem(new GuiItem(designerItem, event -> {
            PortalDesignerGUI.openSelector(player);
        }), 4, 1);
        filledSlots.add("4,1");

        // 5. Set Spawn Location
        ItemStack compassItem = new ItemStack(Material.COMPASS);
        ItemMeta compassMeta = compassItem.getItemMeta();
        if (compassMeta != null) {
            compassMeta.setDisplayName("§d§lĐặt Điểm Xuất Phát");
            compassMeta.setLore(Arrays.asList(
                    "§7Đặt điểm xuất phát tại vị trí hiện tại của bạn",
                    "§7cho phụ bản đã chọn.",
                    "",
                    "§e▶ Nhấp để chọn phụ bản"
            ));
            compassItem.setItemMeta(compassMeta);
        }
        pane.addItem(new GuiItem(compassItem, event -> {
            openSetSpawnSelector(player);
        }), 7, 1);
        filledSlots.add("7,1");

        // 6. Portal Expiration Time Editor Button (Clock)
        ItemStack timeItem = new ItemStack(Material.CLOCK);
        ItemMeta timeMeta = timeItem.getItemMeta();
        if (timeMeta != null) {
            timeMeta.setDisplayName("§e§lCấu Hình Thời Gian Hết Hạn Cổng");
            timeMeta.setLore(Arrays.asList(
                    "§7Thiết lập thời gian tồn tại của cổng",
                    "§7thế giới (world portal) trước khi tự hủy.",
                    "",
                    "§7Thời gian hiện tại: §b" + plugin.getConfigManager().getWorldDungeonSpawnPortalLifetime() + " giây",
                    "",
                    "§e▶ Nhấp để cấu hình"
            ));
            timeItem.setItemMeta(timeMeta);
        }
        pane.addItem(new GuiItem(timeItem, event -> {
            openPortalLifetimeGUI(player);
        }), 6, 1);
        filledSlots.add("6,1");

        // 7. Dungeon Schematic Editor Button
        ItemStack schemEditItem = new ItemStack(Material.PAPER);
        ItemMeta schemEditMeta = schemEditItem.getItemMeta();
        if (schemEditMeta != null) {
            schemEditMeta.setDisplayName("§e§lCấu Hình Schematic Phụ Bản");
            schemEditMeta.setLore(Arrays.asList(
                    "§7Thiết lập schematic sử dụng",
                    "§7cho từng phụ bản khác nhau.",
                    "",
                    "§e▶ Nhấp để cấu hình"
            ));
            schemEditItem.setItemMeta(schemEditMeta);
        }
        pane.addItem(new GuiItem(schemEditItem, event -> {
            openDungeonSchematicSelector(player);
        }), 0, 1);
        filledSlots.add("0,1");

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

    private static void openActiveInstancesGUI(Player player) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();
        Map<String, DungeonInstance> active = plugin.getInstanceManager().getActiveInstances();

        ChestGui gui = new ChestGui(4, "Phụ Bản Hoạt Động");
        gui.setOnTopClick(event -> event.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 4);
        Set<String> filledSlots = new HashSet<>();
        int slot = 0;

        for (DungeonInstance inst : active.values()) {
            if (slot >= 27) break; // Keep space for back button

            ItemStack item = new ItemStack(Material.MAP);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a§lID: " + inst.getInstanceId());
                meta.setLore(Arrays.asList(
                        "§7Phụ bản: §f" + inst.getDungeonId(),
                        "§7Độ khó: §f" + inst.getDifficulty(),
                        "§7Trạng thái: §e" + inst.getState(),
                        "§7Thành viên: §f" + inst.getMembers().size(),
                        "§7Thế giới: §f" + inst.getWorldName(),
                        "",
                        "§e▶ Chuột Trái: Theo dõi (Spectate)",
                        "§c▶ Chuột Phải: Bắt buộc đóng (Force Close)"
                ));
                item.setItemMeta(meta);
            }

            int cx = slot % 9;
            int cy = slot / 9;

            pane.addItem(new GuiItem(item, event -> {
                if (event.isLeftClick()) {
                    player.closeInventory();
                    World w = Bukkit.getWorld(inst.getWorldName());
                    if (w != null) {
                        player.teleport(w.getSpawnLocation());
                        player.setGameMode(GameMode.SPECTATOR);
                        player.sendMessage("§a[LovelyFrontier] Đang theo dõi phụ bản " + inst.getInstanceId());
                    } else {
                        player.sendMessage("§cKhông tìm thấy thế giới phụ bản.");
                    }
                } else if (event.isRightClick()) {
                    player.closeInventory();
                    player.sendMessage("§eĐang bắt buộc đóng phụ bản " + inst.getInstanceId() + "...");
                    plugin.getInstanceManager().beginCleanup(inst.getInstanceId());
                    player.sendMessage("§aĐã kích hoạt dọn dẹp phụ bản.");
                }
            }), cx, cy);

            filledSlots.add(cx + "," + cy);
            slot++;
        }

        // Back button at bottom right
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) backMeta.setDisplayName("§cQuay Lại");
        back.setItemMeta(backMeta);
        pane.addItem(new GuiItem(back, event -> {
            open(player);
        }), 8, 3);
        filledSlots.add("8,3");

        // Background Glass
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        if (bgMeta != null) bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        GuiItem bgItem = new GuiItem(bg, event -> {});
        for (int cx = 0; cx < 9; cx++) {
            for (int cy = 0; cy < 4; cy++) {
                if (!filledSlots.contains(cx + "," + cy)) {
                    pane.addItem(bgItem, cx, cy);
                }
            }
        }

        gui.addPane(pane);
        gui.show(player);
    }

    private static void openSetSpawnSelector(Player player) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();

        ChestGui gui = new ChestGui(3, "Chọn Phụ Bản Đặt Spawn");
        gui.setOnTopClick(event -> event.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        Set<String> filledSlots = new HashSet<>();
        int slot = 0;

        for (DungeonConfig dc : plugin.getDungeonManager().getAllDungeons()) {
            if (slot >= 18) break;

            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§b" + dc.getName());
                meta.setLore(Arrays.asList(
                        "§7ID: §f" + dc.getId(),
                        "",
                        "§e▶ Nhấp để đặt spawn tại vị trí của bạn"
                ));
                item.setItemMeta(meta);
            }

            int cx = slot % 9;
            int cy = slot / 9;

            pane.addItem(new GuiItem(item, event -> {
                player.closeInventory();
                
                Location loc = player.getLocation();
                File file = dc.getConfigFile();
                if (file == null) {
                    file = new File(new File(plugin.getDataFolder(), "dungeons"), dc.getId() + ".yml");
                }

                if (!file.exists()) {
                    player.sendMessage("§cKhông tìm thấy tệp cấu hình dungeons/" + file.getName() + ".");
                    return;
                }

                try {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                    yaml.set("spawn.x", loc.getX());
                    yaml.set("spawn.y", loc.getY());
                    yaml.set("spawn.z", loc.getZ());
                    yaml.set("spawn.yaw", loc.getYaw());
                    yaml.set("spawn.pitch", loc.getPitch());
                    yaml.save(file);

                    // Reload dungeon configurations
                    plugin.getDungeonManager().reload();

                    player.sendMessage(String.format("§aĐã cập nhật điểm xuất phát cho %s tại %.2f, %.2f, %.2f!",
                            dc.getName(), loc.getX(), loc.getY(), loc.getZ()));
                } catch (Exception e) {
                    player.sendMessage("§cKhông thể lưu tệp cấu hình: " + e.getMessage());
                    plugin.getLogger().severe("Error saving dungeon spawn: " + e.getMessage());
                }
            }), cx, cy);

            filledSlots.add(cx + "," + cy);
            slot++;
        }

        // Back button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) backMeta.setDisplayName("§cQuay Lại");
        back.setItemMeta(backMeta);
        pane.addItem(new GuiItem(back, event -> {
            open(player);
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

    private static void openPortalLifetimeGUI(Player player) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();
        int currentVal = plugin.getConfigManager().getWorldDungeonSpawnPortalLifetime();

        ChestGui gui = new ChestGui(3, "Thời Gian Biến Mất Của Cổng");
        gui.setOnTopClick(event -> event.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        Set<String> filledSlots = new HashSet<>();

        // Info Item at Center Slot (1,1)
        ItemStack info = new ItemStack(Material.CLOCK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§b§lThời Gian Hiện Tại");
            infoMeta.setLore(Arrays.asList(
                    "§7Giá trị: §e" + currentVal + " giây §7(" + (currentVal / 60) + " phút)",
                    "",
                    "§eSử dụng các nút bên dưới để tăng/giảm."
            ));
            info.setItemMeta(infoMeta);
        }
        pane.addItem(new GuiItem(info, event -> {}), 4, 1);
        filledSlots.add("4,1");

        // Decrement 60s
        ItemStack dec60 = new ItemStack(Material.REDSTONE);
        ItemMeta dec60Meta = dec60.getItemMeta();
        if (dec60Meta != null) {
            dec60Meta.setDisplayName("§c§l-60 Giây");
            dec60Meta.setLore(Collections.singletonList("§7Giảm thời gian đi 1 phút"));
            dec60.setItemMeta(dec60Meta);
        }
        pane.addItem(new GuiItem(dec60, event -> {
            int newVal = Math.max(60, currentVal - 60);
            plugin.getConfigManager().savePortalLifetime(newVal);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            openPortalLifetimeGUI(player);
        }), 2, 1);
        filledSlots.add("2,1");

        // Decrement 10s
        ItemStack dec10 = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta dec10Meta = dec10.getItemMeta();
        if (dec10Meta != null) {
            dec10Meta.setDisplayName("§c§l-10 Giây");
            dec10Meta.setLore(Collections.singletonList("§7Giảm thời gian đi 10 giây"));
            dec10.setItemMeta(dec10Meta);
        }
        pane.addItem(new GuiItem(dec10, event -> {
            int newVal = Math.max(10, currentVal - 10);
            plugin.getConfigManager().savePortalLifetime(newVal);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            openPortalLifetimeGUI(player);
        }), 3, 1);
        filledSlots.add("3,1");

        // Increment 10s
        ItemStack inc10 = new ItemStack(Material.GLOWSTONE_DUST);
        ItemMeta inc10Meta = inc10.getItemMeta();
        if (inc10Meta != null) {
            inc10Meta.setDisplayName("§a§l+10 Giây");
            inc10Meta.setLore(Collections.singletonList("§7Tăng thời gian thêm 10 giây"));
            inc10.setItemMeta(inc10Meta);
        }
        pane.addItem(new GuiItem(inc10, event -> {
            int newVal = currentVal + 10;
            plugin.getConfigManager().savePortalLifetime(newVal);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            openPortalLifetimeGUI(player);
        }), 5, 1);
        filledSlots.add("5,1");

        // Increment 60s
        ItemStack inc60 = new ItemStack(Material.GLOWSTONE);
        ItemMeta inc60Meta = inc60.getItemMeta();
        if (inc60Meta != null) {
            inc60Meta.setDisplayName("§a§l+60 Giây");
            inc60Meta.setLore(Collections.singletonList("§7Tăng thời gian thêm 1 phút"));
            inc60.setItemMeta(inc60Meta);
        }
        pane.addItem(new GuiItem(inc60, event -> {
            int newVal = currentVal + 60;
            plugin.getConfigManager().savePortalLifetime(newVal);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            openPortalLifetimeGUI(player);
        }), 6, 1);
        filledSlots.add("6,1");

        // Back Button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§lQuay Lại");
            backMeta.setLore(Collections.singletonList("§7Quay lại giao diện Admin Panel"));
            back.setItemMeta(backMeta);
        }
        pane.addItem(new GuiItem(back, event -> {
            open(player);
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

    private static void openDungeonSchematicSelector(Player player) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();

        ChestGui gui = new ChestGui(3, "Chọn Phụ Bản Đổi Schematic");
        gui.setOnTopClick(event -> event.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        Set<String> filledSlots = new HashSet<>();
        int slot = 0;

        for (DungeonConfig dc : plugin.getDungeonManager().getAllDungeons()) {
            if (slot >= 18) break;

            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§b" + dc.getName());
                meta.setLore(Arrays.asList(
                        "§7ID: §f" + dc.getId(),
                        "§7Schematic hiện tại: §e" + dc.getSchematicPath(),
                        "",
                        "§e▶ Nhấp để chọn schematic mới"
                ));
                item.setItemMeta(meta);
            }

            int cx = slot % 9;
            int cy = slot / 9;

            pane.addItem(new GuiItem(item, event -> {
                openSchematicChooser(player, dc);
            }), cx, cy);

            filledSlots.add(cx + "," + cy);
            slot++;
        }

        // Back button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) backMeta.setDisplayName("§cQuay Lại");
        back.setItemMeta(backMeta);
        pane.addItem(new GuiItem(back, event -> {
            open(player);
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

    private static void openSchematicChooser(Player player, DungeonConfig dc) {
        LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();

        ChestGui gui = new ChestGui(4, "Chọn Schematic");
        gui.setOnTopClick(event -> event.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 4);
        Set<String> filledSlots = new HashSet<>();
        int slot = 0;

        File schematicsDir = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsDir.exists()) {
            schematicsDir.mkdirs();
        }

        File[] files = schematicsDir.listFiles((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));

            for (File file : files) {
                if (slot >= 27) break;

                String fileName = file.getName();
                ItemStack item = new ItemStack(Material.PAPER);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§a" + fileName);
                    meta.setLore(Arrays.asList(
                            "§7Nhấp để áp dụng schematic này",
                            "§7cho phụ bản: §f" + dc.getName(),
                            "§7(ID: " + dc.getId() + ")"
                    ));
                    item.setItemMeta(meta);
                }

                int cx = slot % 9;
                int cy = slot / 9;

                pane.addItem(new GuiItem(item, event -> {
                    player.closeInventory();
                    player.sendMessage("§eĐang cập nhật schematic cho " + dc.getName() + "...");

                    File configFile = dc.getConfigFile();
                    if (configFile == null) {
                        configFile = new File(new File(plugin.getDataFolder(), "dungeons"), dc.getId() + ".yml");
                    }
                    if (!configFile.exists()) {
                        player.sendMessage("§cKhông tìm thấy tệp cấu hình dungeons/" + configFile.getName() + ".");
                        return;
                    }

                    File finalConfigFile = configFile;
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        try {
                            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(finalConfigFile);
                            yaml.set("schematic_path", fileName);
                            yaml.save(finalConfigFile);

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                plugin.getDungeonManager().reload();
                                player.sendMessage("§a[LovelyFrontier] Đã cập nhật schematic cho " + dc.getName() + " thành: §e" + fileName);
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                            });
                        } catch (Exception e) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.sendMessage("§cKhông thể lưu tệp cấu hình: " + e.getMessage());
                            });
                            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to save dungeon config for " + dc.getId(), e);
                        }
                    });
                }), cx, cy);

                filledSlots.add(cx + "," + cy);
                slot++;
            }
        }

        // Back button at bottom right (8,3)
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) backMeta.setDisplayName("§cQuay Lại");
        back.setItemMeta(backMeta);
        pane.addItem(new GuiItem(back, event -> {
            openDungeonSchematicSelector(player);
        }), 8, 3);
        filledSlots.add("8,3");

        // Background Glass
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        if (bgMeta != null) bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        GuiItem bgItem = new GuiItem(bg, event -> {});
        for (int cx = 0; cx < 9; cx++) {
            for (int cy = 0; cy < 4; cy++) {
                if (!filledSlots.contains(cx + "," + cy)) {
                    pane.addItem(bgItem, cx, cy);
                }
            }
        }

        gui.addPane(pane);
        gui.show(player);
    }
}
