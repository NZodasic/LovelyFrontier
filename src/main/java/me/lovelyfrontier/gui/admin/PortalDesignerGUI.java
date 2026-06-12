package me.lovelyfrontier.gui.admin;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.DungeonConfig;
import me.lovelyfrontier.model.PortalType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PortalDesignerGUI implements Listener {

    private static final LovelyFrontierPlugin plugin = LovelyFrontierPlugin.getInstance();
    public static final Map<UUID, DesignerSession> sessions = new ConcurrentHashMap<>();

    // Slots for the 4x4 pattern grid
    private static final int[] GRID_SLOTS = {
        0, 1, 2, 3,
        9, 10, 11, 12,
        18, 19, 20, 21,
        27, 28, 29, 30
    };
    private static final Set<Integer> GRID_SLOTS_SET = new HashSet<>();
    static {
        for (int slot : GRID_SLOTS) {
            GRID_SLOTS_SET.add(slot);
        }
    }

    private static final int TRIGGER_SLOT = 15;
    private static final int ACTION_SLOT = 24;
    private static final int TRIGGER_METHOD_SLOT = 33;
    private static final int SAVE_SLOT = 49;
    private static final int BACK_SLOT = 53;

    public static class DesignerSession {
        public String portalId;
        public String action;
        public List<String> actionsList = new ArrayList<>();
        public int actionIndex = 0;
        
        public String triggerMethod = "DROP";
        public List<String> triggerMethodsList = Arrays.asList("DROP", "RIGHT_CLICK", "LEFT_CLICK");
        public int triggerMethodIndex = 0;
    }

    /**
     * Opens the portal type selector GUI.
     */
    public static void openSelector(Player player) {
        ChestGuiWrapper gui = new ChestGuiWrapper(3, "Chọn Cổng Để Thiết Kế");
        
        // Add existing portals
        int slot = 0;
        for (PortalType pt : plugin.getPortalManager().getPortalTypes().values()) {
            if (slot >= 18) break;

            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e§lCổng: " + pt.getId());
                meta.setLore(Arrays.asList(
                        "§7Trigger: §f" + pt.getTriggerItem().name(),
                        "§7Hành động: §f" + pt.getAction(),
                        "",
                        "§e▶ Nhấp để chỉnh sửa"
                ));
                item.setItemMeta(meta);
            }

            final String ptId = pt.getId();
            gui.addItem(slot, item, event -> {
                openDesigner(player, ptId);
            });
            slot++;
        }

        // Create new portal button
        ItemStack createItem = new ItemStack(Material.EMERALD);
        ItemMeta createMeta = createItem.getItemMeta();
        if (createMeta != null) {
            createMeta.setDisplayName("§a§lTạo Cổng Mới");
            createMeta.setLore(Arrays.asList(
                    "§7Thiết kế một loại cổng phụ bản mới.",
                    "",
                    "§e▶ Nhấp để bắt đầu"
            ));
            createItem.setItemMeta(createMeta);
        }
        gui.addItem(22, createItem, event -> {
            player.closeInventory();
            player.sendMessage("§e[LovelyFrontier] Vui lòng nhập ID cho cổng mới trong chat:");
            
            // Set up prompt state using a temporary map or class
            plugin.getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent chatEvent) {
                    if (chatEvent.getPlayer().getUniqueId().equals(player.getUniqueId())) {
                        chatEvent.setCancelled(true);
                        String inputId = chatEvent.getMessage().trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
                        if (inputId.isEmpty()) {
                            player.sendMessage("§cID không hợp lệ. Vui lòng thử lại bằng cách nhấp vào nút Tạo Cổng.");
                            return;
                        }
                        
                        // Check if already exists
                        if (plugin.getPortalManager().getPortalType(inputId) != null) {
                            player.sendMessage("§cCổng có ID này đã tồn tại.");
                            return;
                        }

                        // Unregister listener
                        org.bukkit.event.HandlerList.unregisterAll(this);

                        // Open designer for new portal
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            openDesigner(player, inputId);
                        });
                    }
                }
            }, plugin);
        });

        // Back button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) backMeta.setDisplayName("§cQuay Lại");
        back.setItemMeta(backMeta);
        gui.addItem(26, back, event -> {
            AdminMainGUI.open(player);
        });

        gui.fillBackground();
        gui.open(player);
    }

    /**
     * Opens the 4x4 Portal Designer GUI.
     */
    public static void openDesigner(Player player, String portalId) {
        DesignerSession session = new DesignerSession();
        session.portalId = portalId;

        // Initialize action list
        session.actionsList.add("OPEN_MENU");
        session.actionsList.add("RANDOM_DUNGEON");
        for (DungeonConfig dc : plugin.getDungeonManager().getAllDungeons()) {
            session.actionsList.add("SPECIFIC:" + dc.getId());
        }

        PortalType existing = plugin.getPortalManager().getPortalType(portalId);
        if (existing != null) {
            session.action = existing.getAction();
            int idx = session.actionsList.indexOf(session.action);
            if (idx >= 0) {
                session.actionIndex = idx;
            } else {
                session.actionsList.add(session.action);
                session.actionIndex = session.actionsList.size() - 1;
            }
            
            session.triggerMethod = existing.getTriggerMethod() != null ? existing.getTriggerMethod() : "DROP";
            int tIdx = session.triggerMethodsList.indexOf(session.triggerMethod);
            if (tIdx >= 0) {
                session.triggerMethodIndex = tIdx;
            }
        } else {
            session.action = "OPEN_MENU";
            session.actionIndex = 0;
            session.triggerMethod = "DROP";
            session.triggerMethodIndex = 0;
        }

        sessions.put(player.getUniqueId(), session);

        Inventory inv = Bukkit.createInventory(null, 54, "Thiết Kế Cổng: " + portalId);

        // Fill background
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        if (bgMeta != null) bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);

        for (int i = 0; i < 54; i++) {
            if (!GRID_SLOTS_SET.contains(i) && i != TRIGGER_SLOT && i != ACTION_SLOT && i != TRIGGER_METHOD_SLOT) {
                inv.setItem(i, bg);
            }
        }

        // Action block
        updateActionSlot(inv, session);

        // Trigger Method block
        updateTriggerMethodSlot(inv, session);

        // Save button
        ItemStack save = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta saveMeta = save.getItemMeta();
        if (saveMeta != null) {
            saveMeta.setDisplayName("§a§lLưu Thiết Lập");
            saveMeta.setLore(Arrays.asList(
                    "§7Lưu cổng phụ bản này vào cấu hình.",
                    "§e▶ Nhấp để lưu"
            ));
            save.setItemMeta(saveMeta);
        }
        inv.setItem(SAVE_SLOT, save);

        // Back button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§lQuay Lại");
            backMeta.setLore(Collections.singletonList("§7Hủy bỏ và quay lại"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(BACK_SLOT, back);

        // Pre-fill existing data if editing
        if (existing != null) {
            // Pre-fill trigger item
            inv.setItem(TRIGGER_SLOT, new ItemStack(existing.getTriggerItem()));

            // Pre-fill pattern blocks
            String[] pattern = existing.getPattern();
            Map<Character, Material> keys = existing.getPatternKeys();
            for (int r = 0; r < Math.min(pattern.length, 4); r++) {
                String row = pattern[r];
                for (int c = 0; c < Math.min(row.length(), 4); c++) {
                    char ch = row.charAt(c);
                    Material mat = keys.get(ch);
                    if (ch != ' ' && mat != null) {
                        inv.setItem(GRID_SLOTS[r * 4 + c], new ItemStack(mat));
                    }
                }
            }
        }

        player.openInventory(inv);
    }

    private static void updateActionSlot(Inventory inv, DesignerSession session) {
        ItemStack actionItem = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta actionMeta = actionItem.getItemMeta();
        if (actionMeta != null) {
            actionMeta.setDisplayName("§d§lHành Động Kích Hoạt");
            actionMeta.setLore(Arrays.asList(
                    "§7Hành động hiện tại: §b" + session.action,
                    "",
                    "§e▶ Nhấp để chuyển đổi hành động"
            ));
            actionItem.setItemMeta(actionMeta);
        }
        inv.setItem(ACTION_SLOT, actionItem);
    }

    private static void updateTriggerMethodSlot(Inventory inv, DesignerSession session) {
        ItemStack methodItem = new ItemStack(Material.HOPPER);
        ItemMeta methodMeta = methodItem.getItemMeta();
        if (methodMeta != null) {
            methodMeta.setDisplayName("§e§lCách Thức Kích Hoạt");
            String displayMethod = session.triggerMethod;
            if (session.triggerMethod.equals("DROP")) displayMethod = "Ném Vật Phẩm (DROP)";
            else if (session.triggerMethod.equals("RIGHT_CLICK")) displayMethod = "Nhấp Chuột Phải (RIGHT_CLICK)";
            else if (session.triggerMethod.equals("LEFT_CLICK")) displayMethod = "Nhấp Chuột Trái (LEFT_CLICK)";
            
            methodMeta.setLore(Arrays.asList(
                    "§7Cách kích hoạt hiện tại: §b" + displayMethod,
                    "",
                    "§e▶ Nhấp để chuyển đổi cách thức"
            ));
            methodItem.setItemMeta(methodMeta);
        }
        inv.setItem(TRIGGER_METHOD_SLOT, methodItem);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        DesignerSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        // Check if the clicked inventory is the designer gui
        if (!event.getView().getTitle().startsWith("Thiết Kế Cổng:")) {
            return;
        }

        // If clicked top inventory
        if (event.getRawSlot() < 54 && event.getRawSlot() >= 0) {
            int slot = event.getRawSlot();

            // Allow modifying the 4x4 grid and trigger slot
            if (GRID_SLOTS_SET.contains(slot) || slot == TRIGGER_SLOT) {
                // Let the action happen normally
                return;
            }

            // Otherwise, cancel the click
            event.setCancelled(true);

            if (slot == ACTION_SLOT) {
                // Cycle action
                session.actionIndex = (session.actionIndex + 1) % session.actionsList.size();
                session.action = session.actionsList.get(session.actionIndex);
                updateActionSlot(event.getInventory(), session);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            }
            else if (slot == TRIGGER_METHOD_SLOT) {
                // Cycle trigger method
                session.triggerMethodIndex = (session.triggerMethodIndex + 1) % session.triggerMethodsList.size();
                session.triggerMethod = session.triggerMethodsList.get(session.triggerMethodIndex);
                updateTriggerMethodSlot(event.getInventory(), session);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            }
            else if (slot == SAVE_SLOT) {
                handleSave(player, event.getInventory(), session);
            }
            else if (slot == BACK_SLOT) {
                player.closeInventory();
                openSelector(player);
            }
        } else {
            // Bottom inventory (player inventory) - allow clicks unless transferring to/from background slots
            if (event.isShiftClick()) {
                // Prevent shift clicks into background slots
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().startsWith("Thiết Kế Cổng:")) {
            sessions.remove(event.getPlayer().getUniqueId());
        }
    }

    private static void handleSave(Player player, Inventory inv, DesignerSession session) {
        // 1. Get trigger item
        ItemStack triggerStack = inv.getItem(TRIGGER_SLOT);
        if (triggerStack == null || triggerStack.getType() == Material.AIR) {
            player.sendMessage("§c[LovelyFrontier] Bạn phải đặt một Vật Phẩm Kích Hoạt (trigger item)!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        Material triggerItem = triggerStack.getType();

        // 2. Build pattern and keys
        List<String> pattern = new ArrayList<>();
        Map<Character, Material> keys = new HashMap<>();
        char nextKey = 'A';
        Map<Material, Character> matToChar = new HashMap<>();

        boolean hasBlocks = false;
        for (int r = 0; r < 4; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < 4; c++) {
                int slot = GRID_SLOTS[r * 4 + c];
                ItemStack item = inv.getItem(slot);
                if (item == null || item.getType() == Material.AIR) {
                    sb.append(' ');
                } else {
                    Material mat = item.getType();
                    hasBlocks = true;
                    if (!matToChar.containsKey(mat)) {
                        matToChar.put(mat, nextKey);
                        keys.put(nextKey, mat);
                        nextKey++;
                    }
                    sb.append(matToChar.get(mat));
                }
            }
            pattern.add(sb.toString());
        }

        if (!hasBlocks) {
            player.sendMessage("§c[LovelyFrontier] Cổng phải có ít nhất một khối cấu trúc!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // 3. Save to portal_types.yml
        File file = new File(plugin.getDataFolder(), "portal_types.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String path = "portal_types." + session.portalId + ".";
        config.set(path + "trigger_item", triggerItem.name());
        config.set(path + "pattern", pattern);
        
        // Clear old keys
        config.set(path + "keys", null);
        for (Map.Entry<Character, Material> entry : keys.entrySet()) {
            config.set(path + "keys." + entry.getKey(), entry.getValue().name());
        }
        config.set(path + "action", session.action);
        config.set(path + "trigger_method", session.triggerMethod);

        try {
            config.save(file);
            player.sendMessage("§a[LovelyFrontier] Đã lưu cổng phụ bản '" + session.portalId + "' thành công!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            
            // Reload portals in memory
            plugin.getPortalManager().loadPortalTypes();
            
            player.closeInventory();
            openSelector(player);
        } catch (Exception e) {
            player.sendMessage("§c[LovelyFrontier] Lỗi khi lưu cấu hình: " + e.getMessage());
            plugin.getLogger().severe("Failed to save portal_types.yml: " + e.getMessage());
        }
    }

    /**
     * Simple GUI wrapper using standard Inventory to prevent external library complex setups.
     */
    private static class ChestGuiWrapper implements Listener {
        private final Inventory inventory;
        private final Map<Integer, java.util.function.Consumer<InventoryClickEvent>> handlers = new HashMap<>();

        public ChestGuiWrapper(int rows, String title) {
            this.inventory = Bukkit.createInventory(null, rows * 9, title);
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }

        public void addItem(int slot, ItemStack item, java.util.function.Consumer<InventoryClickEvent> handler) {
            inventory.setItem(slot, item);
            handlers.put(slot, handler);
        }

        public void fillBackground() {
            ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta bgMeta = bg.getItemMeta();
            if (bgMeta != null) bgMeta.setDisplayName(" ");
            bg.setItemMeta(bgMeta);

            for (int i = 0; i < inventory.getSize(); i++) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, bg);
                }
            }
        }

        public void open(Player player) {
            player.openInventory(inventory);
        }

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (event.getInventory().equals(inventory)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player) {
                    int slot = event.getRawSlot();
                    if (handlers.containsKey(slot)) {
                        handlers.get(slot).accept(event);
                    }
                }
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent event) {
            if (event.getInventory().equals(inventory)) {
                org.bukkit.event.HandlerList.unregisterAll(this);
            }
        }
    }
}
