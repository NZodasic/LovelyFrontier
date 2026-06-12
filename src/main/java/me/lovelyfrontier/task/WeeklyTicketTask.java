package me.lovelyfrontier.task;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.DungeonConfig;
import me.lovelyfrontier.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WeeklyTicketTask extends BukkitRunnable {

    private final LovelyFrontierPlugin plugin;

    public WeeklyTicketTask(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks a player's eligibility and gives them a weekly free ticket.
     */
    public void checkTicket(Player player) {
        if (!plugin.getConfigManager().isWeeklyFreeTicketsEnabled()) return;

        UUID uuid = player.getUniqueId();
        
        // 1. Check if flagged as alt
        plugin.getPlayerProfileRepository().isFlagged(uuid).thenAccept(flagged -> {
            if (flagged) return;

            // 2. Check playtime gate
            plugin.getPlayerProfileRepository().getPlaytime(uuid).thenAccept(playtime -> {
                double reqPlaytime = plugin.getConfigManager().getAntiAbuseMinPlaytimeHours();
                if (playtime < reqPlaytime) return;

                // 3. Check last free ticket time
                plugin.getPlayerProfileRepository().getLastFreeTicketAt(uuid).thenAccept(lastTime -> {
                    long now = System.currentTimeMillis();
                    // 7 days in milliseconds = 604800000L
                    if (lastTime == null || now - lastTime.getTime() >= 604800000L) {
                        
                        // Grant ticket
                        int amount = plugin.getConfigManager().getWeeklyFreeTicketsAmount();
                        
                        CompletableFuture<Boolean> ticketFuture;
                        if (plugin.getConfigManager().isTicketsUniversalTicket()) {
                            ticketFuture = plugin.getTicketRepository().addTicket(uuid, "UNIVERSAL", "UNIVERSAL", amount);
                        } else {
                            // Non-universal: Add ticket for all dungeons (or NORMAL diff)
                            CompletableFuture<?>[] futures = plugin.getDungeonManager().getAllDungeons().stream()
                                    .map(dungeon -> plugin.getTicketRepository().addTicket(uuid, dungeon.getId(), "NORMAL", amount))
                                    .toArray(CompletableFuture[]::new);
                            ticketFuture = CompletableFuture.allOf(futures).thenApply(v -> true);
                        }

                        ticketFuture.thenAccept(success -> {
                            if (success) {
                                // Update DB time
                                plugin.getPlayerProfileRepository().updateLastFreeTicketAt(uuid, new Timestamp(now));

                                // Notify player on main thread (R-001)
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (player.isOnline()) {
                                        player.sendMessage("§a[LovelyFrontier] Bạn đã nhận được vé miễn phí hàng tuần!");
                                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                                    }
                                });
                            }
                        });
                    }
                });
            });
        });
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkTicket(player);
        }
    }
}
