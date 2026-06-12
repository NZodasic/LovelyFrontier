package me.lovelyfrontier.listener;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.DungeonInstance;
import me.lovelyfrontier.model.InstanceState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;

public class DungeonCommandListener implements Listener {

    private final LovelyFrontierPlugin plugin;

    public DungeonCommandListener(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        
        // Admins can use any commands
        if (player.hasPermission("lf.admin")) {
            return;
        }

        // Check if player is in an active dungeon instance
        DungeonInstance instance = plugin.getInstanceManager().getInstanceByPlayer(player.getUniqueId());
        if (instance == null || instance.getState() != InstanceState.ACTIVE) {
            return;
        }

        // Parse command name
        String message = event.getMessage().trim();
        if (message.startsWith("/")) {
            message = message.substring(1);
        }

        String[] parts = message.split("\\s+");
        if (parts.length == 0) return;
        
        String commandLabel = parts[0].toLowerCase();

        // Check whitelist
        List<String> allowed = plugin.getConfigManager().getCommandFilterAllowedCommands();
        if (!allowed.contains(commandLabel)) {
            event.setCancelled(true);
            player.sendMessage("§cBạn không thể sử dụng lệnh này khi đang ở trong phụ bản.");
        }
    }
}
