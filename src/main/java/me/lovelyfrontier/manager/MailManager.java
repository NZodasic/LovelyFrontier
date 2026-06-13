package me.lovelyfrontier.manager;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.repository.MailRepository;
import me.lovelyfrontier.util.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MailManager {

    private final LovelyFrontierPlugin plugin;

    public MailManager(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Queues loot/rewards to lf_mail for a player.
     */
    public CompletableFuture<Boolean> queueLoot(UUID playerUuid, String dungeonId, double money, ItemStack[] items) {
        return plugin.getMailRepository().queueMail(playerUuid, dungeonId, money, items);
    }

    /**
     * Delivers unclaimed mail to a player on login or on request.
     * Must be called from the main thread (or schedule it inside).
     */
    public void deliverMailOnLogin(Player player) {
        plugin.getMailRepository().getUnclaimedMail(player.getUniqueId()).thenAccept(mailList -> {
            if (mailList.isEmpty()) return;

            // Notify player on login
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(MessageUtil.get("mail_received"));
                }
            });
        });
    }

    /**
     * Handles the /lf mail command to claim all pending mail.
     */
    public void claimMail(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getMailRepository().getUnclaimedMail(uuid).thenAccept(mailList -> {
            if (mailList.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(MessageUtil.get("mail_empty"));
                    }
                });
                return;
            }

            List<CompletableFuture<MailRepository.DbMail>> claimFutures = new ArrayList<>();
            for (MailRepository.DbMail mail : mailList) {
                claimFutures.add(plugin.getMailRepository().markClaimed(mail.mailId)
                        .thenApply(claimed -> claimed ? mail : null));
            }

            CompletableFuture.allOf(claimFutures.toArray(new CompletableFuture[0])).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                double totalMoney = 0.0;
                List<ItemStack> allItems = new ArrayList<>();

                for (CompletableFuture<MailRepository.DbMail> claimFuture : claimFutures) {
                    MailRepository.DbMail mail = claimFuture.join();
                    if (mail == null) {
                        continue;
                    }

                    totalMoney += mail.money;
                    if (mail.items != null) {
                        for (ItemStack item : mail.items) {
                            if (item != null) {
                                allItems.add(item);
                            }
                        }
                    }
                }

                // Economy payout
                if (totalMoney > 0) {
                    Economy economy = plugin.getEconomy();
                    if (economy != null) {
                        try {
                            economy.depositPlayer(player, totalMoney);
                        } catch (Exception e) {
                            plugin.getLogger().severe("[LF] Vault economy error during mail delivery: " + e.getMessage());
                        }
                    }
                }

                // Item payout
                int itemsCount = 0;
                for (ItemStack item : allItems) {
                    if (item == null) continue;
                    itemsCount += item.getAmount();
                    HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(item);
                    for (ItemStack remItem : remaining.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), remItem);
                    }
                }

                player.sendMessage(MessageUtil.get("mail_claimed", "count", itemsCount));
            }));
        });
    }
}
