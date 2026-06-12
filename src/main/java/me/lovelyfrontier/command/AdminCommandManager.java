package me.lovelyfrontier.command;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.DungeonInstance;
import me.lovelyfrontier.model.DungeonConfig;
import me.lovelyfrontier.model.InstanceState;
import me.lovelyfrontier.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AdminCommandManager implements CommandExecutor, TabCompleter {

    private final LovelyFrontierPlugin plugin;

    public AdminCommandManager(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lf.admin")) {
            sender.sendMessage(MessageUtil.get("no_permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                if (!sender.hasPermission("lf.admin.reload")) {
                    sender.sendMessage(MessageUtil.get("no_permission"));
                    return true;
                }
                plugin.getConfigManager().reload();
                plugin.getDungeonManager().reload();
                me.lovelyfrontier.util.MessageUtil.load(plugin);
                sender.sendMessage("§a[LovelyFrontier] Đã tải lại tệp cấu hình config.yml, dữ liệu phụ bản và thông điệp!");
                break;

            case "instances":
                if (!sender.hasPermission("lf.admin.instances")) {
                    sender.sendMessage(MessageUtil.get("no_permission"));
                    return true;
                }
                handleInstances(sender);
                break;

            case "forceclose":
                if (!sender.hasPermission("lf.admin.forceclose")) {
                    sender.sendMessage(MessageUtil.get("no_permission"));
                    return true;
                }
                handleForceClose(sender, args);
                break;

            case "forcestart":
                handleForceStart(sender, args);
                break;

            case "forcejoin":
                handleForceJoin(sender, args);
                break;

            case "give":
                if (!sender.hasPermission("lf.admin.give")) {
                    sender.sendMessage(MessageUtil.get("no_permission"));
                    return true;
                }
                handleGive(sender, args);
                break;

            case "setspawn":
                if (!sender.hasPermission("lf.admin.setspawn")) {
                    sender.sendMessage(MessageUtil.get("no_permission"));
                    return true;
                }
                handleSetSpawn(sender, args);
                break;

            case "edit":
                if (!sender.hasPermission("lf.admin.edit")) {
                    sender.sendMessage(MessageUtil.get("no_permission"));
                    return true;
                }
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    me.lovelyfrontier.gui.admin.AdminMainGUI.open(p);
                } else {
                    sender.sendMessage("Chỉ người chơi mới có thể dùng lệnh này.");
                }
                break;

            case "shop":
                if (args.length > 1 && args[1].equalsIgnoreCase("edit")) {
                    if (!sender.hasPermission("lf.admin.shop")) {
                        sender.sendMessage(MessageUtil.get("no_permission"));
                        return true;
                    }
                    if (sender instanceof Player) {
                        Player p = (Player) sender;
                        me.lovelyfrontier.gui.admin.ShopEditorGUI.open(p);
                    } else {
                        sender.sendMessage("Chỉ người chơi mới có thể dùng lệnh này.");
                    }
                } else {
                    sendHelp(sender);
                }
                break;

            case "worldportal":
                if (!sender.hasPermission("lf.admin.worldspawn.manage")) {
                    sender.sendMessage(MessageUtil.get("no_permission"));
                    return true;
                }
                handleWorldPortal(sender, args);
                break;

            case "import":
                handleImport(sender, args);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleInstances(CommandSender sender) {
        Map<String, DungeonInstance> active = plugin.getInstanceManager().getActiveInstances();
        if (active.isEmpty()) {
            sender.sendMessage("§e[LovelyFrontier] Hiện không có phụ bản nào đang hoạt động.");
            return;
        }

        sender.sendMessage("§a--- Danh sách Phụ Bản Đang Hoạt Động (" + active.size() + ") ---");
        for (DungeonInstance inst : active.values()) {
            sender.sendMessage(String.format("§2- ID: §f%s §7| §2Phụ bản: §f%s §7| §2Độ khó: §f%s §7| §2Trạng thái: §e%s",
                    inst.getInstanceId(), inst.getDungeonId(), inst.getDifficulty(), inst.getState()));
        }
    }

    private void handleForceClose(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cCách dùng: /lfa forceclose <instance_id>");
            return;
        }

        String instanceId = args[1];
        DungeonInstance instance = plugin.getInstanceManager().getInstance(instanceId);
        if (instance == null) {
            sender.sendMessage("§cKhông tìm thấy phụ bản hoạt động với ID: " + instanceId);
            return;
        }

        sender.sendMessage("§eĐang bắt buộc đóng phụ bản " + instanceId + "...");
        plugin.getInstanceManager().beginCleanup(instanceId);
        sender.sendMessage("§aĐã kích hoạt dọn dẹp phụ bản.");
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cCách dùng: /lfa give <người chơi> <phụ bản> <độ khó> [số lượng]");
            return;
        }

        String playerName = args[1];
        String dungeonId = args[2];
        String difficulty = args[3].toUpperCase();
        int quantity = 1;

        if (args.length >= 5) {
            try {
                quantity = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cSố lượng vé phải là số nguyên.");
                return;
            }
        }

        // Verify dungeon ID exists
        if (!dungeonId.equalsIgnoreCase("UNIVERSAL") && plugin.getDungeonManager().getDungeon(dungeonId) == null) {
            sender.sendMessage("§cKhông tìm thấy phụ bản có ID: " + dungeonId);
            return;
        }

        // Resolve player (works offline too)
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        UUID targetUuid = target.getUniqueId();

        int finalQuantity = quantity;
        plugin.getTicketRepository().addTicket(targetUuid, dungeonId, difficulty, quantity)
                .thenAccept(success -> {
                    if (success) {
                        sender.sendMessage(String.format("§aĐã trao %d vé phụ bản %s (%s) cho %s.",
                                finalQuantity, dungeonId, difficulty, playerName));
                        Player onlinePlayer = target.getPlayer();
                        if (onlinePlayer != null) {
                            onlinePlayer.sendMessage(String.format("§aBạn nhận được %d vé phụ bản %s (%s) từ quản trị viên.",
                                    finalQuantity, dungeonId, difficulty));
                        }
                    } else {
                        sender.sendMessage("§cCó lỗi xảy ra khi thêm vé vào cơ sở dữ liệu.");
                    }
                });
    }

    private void handleSetSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Chỉ người chơi mới có thể đặt điểm xuất phát.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cCách dùng: /lfa setspawn <dungeon_id>");
            return;
        }

        Player player = (Player) sender;
        String dungeonId = args[1];

        DungeonConfig dungeon = plugin.getDungeonManager().getDungeon(dungeonId);
        if (dungeon == null) {
            sender.sendMessage("§cKhông tìm thấy cấu hình phụ bản với ID: " + dungeonId);
            return;
        }

        Location loc = player.getLocation();
        File file = dungeon.getConfigFile();
        if (file == null) {
            file = new File(new File(plugin.getDataFolder(), "dungeons"), dungeonId + ".yml");
        }

        if (!file.exists()) {
            sender.sendMessage("§cKhông tìm thấy tệp cấu hình dungeons/" + file.getName() + ".");
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
                    dungeon.getName(), loc.getX(), loc.getY(), loc.getZ()));
        } catch (Exception e) {
            player.sendMessage("§cKhông thể lưu tệp cấu hình: " + e.getMessage());
            plugin.getLogger().severe("Error saving dungeon spawn: " + e.getMessage());
        }
    }

    private void handleForceStart(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cCách dùng: /lfa forcestart <dungeon_id> [difficulty] [player]");
            return;
        }

        String dungeonId = args[1];
        if (plugin.getDungeonManager().getDungeon(dungeonId) == null) {
            sender.sendMessage("§cKhông tìm thấy cấu hình phụ bản: " + dungeonId);
            return;
        }

        String difficulty = "NORMAL";
        if (args.length >= 3) {
            difficulty = args[2].toUpperCase();
        }

        Player targetPlayer = null;
        if (args.length >= 4) {
            targetPlayer = Bukkit.getPlayer(args[3]);
            if (targetPlayer == null) {
                sender.sendMessage("§cKhông tìm thấy người chơi: " + args[3]);
                return;
            }
        } else {
            if (sender instanceof Player) {
                targetPlayer = (Player) sender;
            } else {
                sender.sendMessage("§cChỉ định người chơi: /lfa forcestart <dungeon_id> <difficulty> <player>");
                return;
            }
        }

        List<UUID> partyMembers = new ArrayList<>();
        try {
            net.Indyuce.mmocore.api.player.PlayerData data = net.Indyuce.mmocore.api.player.PlayerData.get(targetPlayer.getUniqueId());
            if (data != null && data.getParty() != null) {
                net.Indyuce.mmocore.party.provided.Party party = (net.Indyuce.mmocore.party.provided.Party) data.getParty();
                if (party != null) {
                    for (net.Indyuce.mmocore.api.player.PlayerData member : party.getOnlineMembers()) {
                        partyMembers.add(member.getUniqueId());
                    }
                }
            }
        } catch (Exception e) {
            // fallback
        }
        if (partyMembers.isEmpty()) {
            partyMembers.add(targetPlayer.getUniqueId());
        }

        sender.sendMessage("§eĐang bắt buộc khởi tạo phụ bản cho " + targetPlayer.getName() + "...");
        plugin.getSessionManager().forceStartDungeon(dungeonId, difficulty, targetPlayer, partyMembers)
                .thenAccept(res -> {
                    if (res.isSuccess()) {
                        sender.sendMessage("§aKhởi tạo thành công! Phụ bản ID: " + res.getInstanceId());
                    } else {
                        sender.sendMessage("§cKhởi tạo thất bại: " + res.getMessage());
                    }
                });
    }

    private void handleForceJoin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cCách dùng: /lfa forcejoin <instance_id> [player]");
            return;
        }

        String instanceId = args[1];
        DungeonInstance instance = plugin.getInstanceManager().getInstance(instanceId);
        if (instance == null) {
            sender.sendMessage("§cKhông tìm thấy phụ bản đang hoạt động với ID: " + instanceId);
            return;
        }

        Player targetPlayer = null;
        if (args.length >= 3) {
            targetPlayer = Bukkit.getPlayer(args[2]);
            if (targetPlayer == null) {
                sender.sendMessage("§cKhông tìm thấy người chơi: " + args[2]);
                return;
            }
        } else {
            if (sender instanceof Player) {
                targetPlayer = (Player) sender;
            } else {
                sender.sendMessage("§cChỉ định người chơi: /lfa forcejoin <instance_id> <player>");
                return;
            }
        }

        Player finalTarget = targetPlayer;
        if (!instance.getMembers().contains(finalTarget.getUniqueId())) {
            instance.getMembers().add(finalTarget.getUniqueId());
        }

        World world = Bukkit.getWorld(instance.getWorldName());
        if (world == null) {
            sender.sendMessage("§cThế giới phụ bản không được tìm thấy.");
            return;
        }

        me.lovelyfrontier.model.DungeonConfig config = plugin.getDungeonManager().getDungeon(instance.getDungeonId());
        if (config == null) {
            sender.sendMessage("§cKhông thể tìm thấy cấu hình phụ bản.");
            return;
        }

        org.bukkit.Location spawnLoc = new org.bukkit.Location(world, config.getSpawnX(), config.getSpawnY(), config.getSpawnZ(), config.getSpawnYaw(), config.getSpawnPitch());
        
        plugin.getInstanceRepository().addMember(instanceId, finalTarget.getUniqueId()).thenAccept(success -> {
            if (success) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    finalTarget.teleport(spawnLoc);
                    finalTarget.sendMessage("§aBạn đã được bắt buộc tham gia phụ bản ID: " + instanceId);
                    sender.sendMessage("§aĐã bắt buộc đưa " + finalTarget.getName() + " vào phụ bản " + instanceId);
                });
            } else {
                sender.sendMessage("§cCó lỗi khi thêm người chơi vào cơ sở dữ liệu phụ bản.");
            }
        });
    }

    private void handleImport(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cCách dùng: /lfa import <tên_schematic>");
            return;
        }

        String schematicName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        File fschematicDir = new File(plugin.getDataFolder(), "fschematic");
        if (!fschematicDir.exists()) {
            fschematicDir.mkdirs();
        }

        File sourceFile = new File(fschematicDir, schematicName);
        if (!sourceFile.exists()) {
            File trySchem = new File(fschematicDir, schematicName + ".schem");
            if (trySchem.exists()) {
                sourceFile = trySchem;
            } else {
                File trySchematic = new File(fschematicDir, schematicName + ".schematic");
                if (trySchematic.exists()) {
                    sourceFile = trySchematic;
                }
            }
        }

        if (!sourceFile.exists()) {
            sender.sendMessage("§cKhông tìm thấy tệp schematic nào có tên: " + schematicName + " trong thư mục fschematic/");
            return;
        }

        File finalSourceFile = sourceFile;
        sender.sendMessage("§eĐang nhập schematic " + finalSourceFile.getName() + "...");
        CompletableFuture.runAsync(() -> {
            try {
                File schematicsDir = new File(plugin.getDataFolder(), "schematics");
                if (!schematicsDir.exists()) {
                    schematicsDir.mkdirs();
                }
                File targetFile = new File(schematicsDir, finalSourceFile.getName());

                java.nio.file.Files.copy(finalSourceFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                sender.sendMessage("§a[LovelyFrontier] Đã nhập thành công schematic: §e" + finalSourceFile.getName());
            } catch (Exception e) {
                sender.sendMessage("§cCó lỗi xảy ra khi nhập schematic: " + e.getMessage());
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to import schematic: " + finalSourceFile.getName(), e);
            }
        });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§a=== Các lệnh Quản Trị Viên LovelyFrontier ===");
        sender.sendMessage("§2/lfa reload §7- Tải lại toàn bộ cấu hình plugin");
        sender.sendMessage("§2/lfa instances §7- Danh sách phụ bản đang hoạt động");
        sender.sendMessage("§2/lfa forceclose <instance_id> §7- Bắt buộc đóng một phụ bản");
        sender.sendMessage("§2/lfa forcestart <dungeon_id> [difficulty] [player] §7- Bắt buộc tạo và vào phụ bản");
        sender.sendMessage("§2/lfa forcejoin <instance_id> [player] §7- Bắt buộc đưa người chơi vào phụ bản hoạt động");
        sender.sendMessage("§2/lfa give <người chơi> <phụ bản> <độ khó> [số lượng] §7- Phát vé cho người chơi");
        sender.sendMessage("§2/lfa setspawn <dungeon_id> §7- Đặt điểm xuất phát tại vị trí hiện tại");
        sender.sendMessage("§2/lfa worldportal <spawn|spawnhere|despawnall|list> §7- Quản lý cổng phụ bản");
        sender.sendMessage("§2/lfa edit §7- Mở giao diện thiết lập phụ bản");
        sender.sendMessage("§2/lfa shop edit §7- Mở giao diện cấu hình cửa hàng");
        sender.sendMessage("§2/lfa import <tên_schematic> §7- Nhập một tệp schematic từ thư mục fschematic");
    }

    private void handleWorldPortal(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cCách dùng: /lfa worldportal <spawn|spawnhere|despawnall|list> ...");
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "spawn": {
                if (args.length < 3) {
                    sender.sendMessage("§cCách dùng: /lfa worldportal spawn <dungeon_id|random> [tên_thế_giới]");
                    return;
                }
                String dungeonId = args[2];
                DungeonConfig dungeon = null;
                if (!dungeonId.equalsIgnoreCase("random")) {
                    dungeon = plugin.getDungeonManager().getDungeon(dungeonId);
                    if (dungeon == null) {
                        sender.sendMessage("§cKhông tìm thấy cấu hình phụ bản với ID: " + dungeonId);
                        return;
                    }
                } else {
                    Collection<DungeonConfig> dungeons = plugin.getDungeonManager().getAllDungeons();
                    if (dungeons.isEmpty()) {
                        sender.sendMessage("§cKhông có phụ bản nào cấu hình.");
                        return;
                    }
                    dungeon = new ArrayList<>(dungeons).get(new Random().nextInt(dungeons.size()));
                }

                World world = null;
                if (args.length >= 4) {
                    world = Bukkit.getWorld(args[3]);
                    if (world == null) {
                        sender.sendMessage("§cKhông tìm thấy thế giới: " + args[3]);
                        return;
                    }
                } else {
                    if (sender instanceof Player) {
                        world = ((Player) sender).getWorld();
                    } else {
                        world = Bukkit.getWorlds().get(0);
                    }
                }

                sender.sendMessage("§eĐang tìm vị trí an toàn để tạo Cổng Phụ Bản cho " + dungeon.getName() + "...");
                plugin.getWorldSpawnManager().forceSpawnRandom(dungeon, world);
                break;
            }

            case "spawnhere": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cChỉ người chơi mới có thể sử dụng lệnh này.");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cCách dùng: /lfa worldportal spawnhere <dungeon_id|random>");
                    return;
                }
                Player player = (Player) sender;
                String dungeonId = args[2];
                DungeonConfig dungeon = null;
                if (!dungeonId.equalsIgnoreCase("random")) {
                    dungeon = plugin.getDungeonManager().getDungeon(dungeonId);
                    if (dungeon == null) {
                        player.sendMessage("§cKhông tìm thấy cấu hình phụ bản với ID: " + dungeonId);
                        return;
                    }
                } else {
                    Collection<DungeonConfig> dungeons = plugin.getDungeonManager().getAllDungeons();
                    if (dungeons.isEmpty()) {
                        player.sendMessage("§cKhông có phụ bản nào cấu hình.");
                        return;
                    }
                    dungeon = new ArrayList<>(dungeons).get(new Random().nextInt(dungeons.size()));
                }

                player.sendMessage("§eĐang tạo Cổng Phụ Bản cho " + dungeon.getName() + " tại vị trí của bạn...");
                Location loc = player.getLocation();
                plugin.getWorldSpawnManager().forceSpawnAt(dungeon, loc);
                break;
            }

            case "despawnall": {
                Map<String, me.lovelyfrontier.repository.PortalRepository.DbPortal> active = plugin.getWorldSpawnManager().getActiveWorldPortals();
                if (active.isEmpty()) {
                    sender.sendMessage("§eKhông có cổng phụ bản nào đang hoạt động.");
                    return;
                }
                int count = active.size();
                List<String> ids = new ArrayList<>(active.keySet());
                for (String id : ids) {
                    plugin.getWorldSpawnManager().despawnPortal(id, false);
                }
                sender.sendMessage("§aĐã dọn dẹp và xóa toàn bộ " + count + " cổng phụ bản.");
                break;
            }

            case "list": {
                Map<String, me.lovelyfrontier.repository.PortalRepository.DbPortal> active = plugin.getWorldSpawnManager().getActiveWorldPortals();
                if (active.isEmpty()) {
                    sender.sendMessage("§eKhông có cổng phụ bản nào đang hoạt động.");
                    return;
                }
                sender.sendMessage("§a--- Danh sách Cổng Phụ Bản Đang Hoạt Động (" + active.size() + ") ---");
                for (me.lovelyfrontier.repository.PortalRepository.DbPortal portal : active.values()) {
                    sender.sendMessage(String.format("§2- ID: §f%s §7| §2Phụ bản: §f%s §7| §2Vị trí: §f%d, %d, %d (%s)",
                            portal.portalId, portal.dungeonId, (int) portal.x, (int) portal.y, (int) portal.z, portal.worldName));
                }
                break;
            }

            default:
                sender.sendMessage("§cCách dùng: /lfa worldportal <spawn|spawnhere|despawnall|list> ...");
                break;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("lf.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filter(Arrays.asList("reload", "instances", "forceclose", "forcestart", "forcejoin", "give", "setspawn", "edit", "shop", "worldportal", "import"), args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("import")) {
                File fschematicDir = new File(plugin.getDataFolder(), "fschematic");
                if (fschematicDir.exists()) {
                    File[] files = fschematicDir.listFiles();
                    if (files != null) {
                        List<String> fileNames = Arrays.stream(files)
                                .filter(File::isFile)
                                .map(File::getName)
                                .collect(Collectors.toList());
                        return filter(fileNames, args[1]);
                    }
                }
                return Collections.emptyList();
            }
            if (sub.equals("worldportal")) {
                return filter(Arrays.asList("spawn", "spawnhere", "despawnall", "list"), args[1]);
            }
            if (sub.equals("forceclose") || sub.equals("forcejoin")) {
                return filter(new ArrayList<>(plugin.getInstanceManager().getActiveInstances().keySet()), args[1]);
            }
            if (sub.equals("setspawn") || sub.equals("forcestart")) {
                List<String> dungeons = plugin.getDungeonManager().getAllDungeons().stream()
                        .map(DungeonConfig::getId)
                        .collect(Collectors.toList());
                return filter(dungeons, args[1]);
            }
            if (sub.equals("give")) {
                List<String> players = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                return filter(players, args[1]);
            }
            if (sub.equals("shop")) {
                return filter(Collections.singletonList("edit"), args[1]);
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("worldportal")) {
                String subSub = args[1].toLowerCase();
                if (subSub.equals("spawn") || subSub.equals("spawnhere")) {
                    List<String> dungeons = plugin.getDungeonManager().getAllDungeons().stream()
                            .map(DungeonConfig::getId)
                            .collect(Collectors.toCollection(ArrayList::new));
                    dungeons.add("random");
                    return filter(dungeons, args[2]);
                }
            }
            if (sub.equalsIgnoreCase("give")) {
                List<String> dungeons = plugin.getDungeonManager().getAllDungeons().stream()
                        .map(DungeonConfig::getId)
                        .collect(Collectors.toCollection(ArrayList::new));
                dungeons.add("UNIVERSAL");
                return filter(dungeons, args[2]);
            }
            if (sub.equalsIgnoreCase("forcestart")) {
                return filter(Arrays.asList("VERY_EASY", "EASY", "NORMAL", "HARD", "VERY_HARD"), args[2]);
            }
            if (sub.equalsIgnoreCase("forcejoin")) {
                List<String> players = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                return filter(players, args[2]);
            }
        }

        if (args.length == 4) {
            String sub = args[0].toLowerCase();
            if (sub.equals("worldportal")) {
                String subSub = args[1].toLowerCase();
                if (subSub.equals("spawn")) {
                    List<String> worlds = Bukkit.getWorlds().stream()
                            .map(World::getName)
                            .collect(Collectors.toList());
                    return filter(worlds, args[3]);
                }
            }
            if (sub.equalsIgnoreCase("give")) {
                return filter(Arrays.asList("VERY_EASY", "EASY", "NORMAL", "HARD", "VERY_HARD"), args[3]);
            }
            if (sub.equalsIgnoreCase("forcestart")) {
                List<String> players = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                return filter(players, args[3]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String query) {
        if (query.isEmpty()) return list;
        String lower = query.toLowerCase();
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
