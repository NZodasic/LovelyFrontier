package me.lovelyfrontier.command;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.DungeonInstance;
import me.lovelyfrontier.model.PlayerSession;
import me.lovelyfrontier.util.MessageUtil;
import me.lovelyfrontier.gui.MainHubGUI;
import me.lovelyfrontier.gui.ShopGUI;
import me.lovelyfrontier.gui.TicketGUI;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PlayerCommandManager implements CommandExecutor, TabCompleter {

    private final LovelyFrontierPlugin plugin;

    public PlayerCommandManager(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Chỉ người chơi mới có thể thực hiện lệnh này.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("lf.use")) {
            player.sendMessage(MessageUtil.get("no_permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("hub")) {
            MainHubGUI.open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "shop":
                if (!player.hasPermission("lf.shop")) {
                    player.sendMessage(MessageUtil.get("no_permission"));
                    return true;
                }
                ShopGUI.open(player);
                break;

            case "tickets":
                TicketGUI.open(player);
                break;

            case "leave":
                handleLeave(player);
                break;

            case "ready":
                handleReady(player);
                break;

            case "vote":
                handleVote(player, args);
                break;

            case "mail":
                plugin.getMailManager().claimMail(player);
                break;

            default:
                player.sendMessage("§cLệnh phụ không xác định. Sử dụng /lf để mở trung tâm.");
                break;
        }

        return true;
    }

    private void handleLeave(Player player) {
        DungeonInstance instance = plugin.getInstanceManager().getInstanceByPlayer(player.getUniqueId());
        if (instance == null) {
            player.sendMessage("§cBạn hiện không ở trong phụ bản nào.");
            return;
        }

        World world = Bukkit.getWorld(instance.getWorldName());
        if (world != null) {
            // Teleport player out
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            player.sendMessage(MessageUtil.get("instance_left"));

            // Check if there are any players left in the world
            // We run this after 1 tick to allow teleportation to complete
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (world.getPlayers().isEmpty()) {
                    plugin.getLogger().info("All players have left instance " + instance.getInstanceId() + ". Triggering cleanup.");
                    plugin.getInstanceManager().beginCleanup(instance.getInstanceId());
                }
            }, 1L);
        }
    }

    private void handleReady(Player player) {
        PlayerSession session = plugin.getSessionManager().findActiveSessionByPlayer(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§cBạn không có phiên vào phụ bản nào hoạt động để xác nhận.");
            return;
        }

        plugin.getSessionManager().submitReady(session.getSessionId(), player.getUniqueId(), true);
    }

    private void handleVote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cCách dùng: /lf vote <yes/no/accept/decline>");
            return;
        }

        PlayerSession session = plugin.getSessionManager().findActiveSessionByPlayer(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§cBạn không có cuộc bầu chọn độ khó nào hoạt động để tham gia.");
            return;
        }

        plugin.getSessionManager().submitVote(session.getSessionId(), player.getUniqueId(), args[1]);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("hub", "shop", "tickets", "leave", "ready", "vote", "mail"));
            if (!sender.hasPermission("lf.shop")) {
                subs.remove("shop");
            }
            return subs;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("vote")) {
            return Arrays.asList("yes", "no", "accept", "decline");
        }
        return Collections.emptyList();
    }
}
