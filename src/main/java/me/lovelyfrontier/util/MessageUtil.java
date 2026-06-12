package me.lovelyfrontier.util;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MessageUtil {

    private static final Map<String, String> MESSAGES = new HashMap<>();
    private static String prefix = "";

    public static void load(JavaPlugin plugin) {
        MESSAGES.clear();

        // 1. Load default messages from resources as fallback
        try (java.io.InputStream defaultStream = plugin.getResource("messages.yml")) {
            if (defaultStream != null) {
                java.io.Reader defaultReader = new java.io.InputStreamReader(defaultStream, java.nio.charset.StandardCharsets.UTF_8);
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(defaultReader);
                for (String key : defaultConfig.getKeys(true)) {
                    if (defaultConfig.isString(key)) {
                        MESSAGES.put(key, defaultConfig.getString(key));
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load default messages.yml from jar resources: " + e.getMessage());
        }

        // 2. Load custom messages from disk (overriding defaults)
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                MESSAGES.put(key, config.getString(key));
            }
        }
        prefix = MESSAGES.getOrDefault("prefix", "");
    }

    public static String getRaw(String key) {
        return MESSAGES.getOrDefault(key, key);
    }

    public static String get(String key, Object... placeholders) {
        String msg = MESSAGES.get(key);
        if (msg == null) {
            return ChatColor.translateAlternateColorCodes('&', "&cMissing message key: " + key);
        }

        // Apply placeholders
        if (placeholders != null) {
            for (int i = 0; i < placeholders.length; i += 2) {
                if (i + 1 < placeholders.length) {
                    String target = "{" + placeholders[i].toString() + "}";
                    String replacement = placeholders[i + 1] != null ? placeholders[i + 1].toString() : "null";
                    msg = msg.replace(target, replacement);
                }
            }
        }

        // Apply prefix if it's not the prefix key itself
        if (!key.equals("prefix") && !prefix.isEmpty()) {
            msg = prefix + msg;
        }

        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public static String getWithoutPrefix(String key, Object... placeholders) {
        String msg = MESSAGES.get(key);
        if (msg == null) {
            return ChatColor.translateAlternateColorCodes('&', "&cMissing message key: " + key);
        }

        if (placeholders != null) {
            for (int i = 0; i < placeholders.length; i += 2) {
                if (i + 1 < placeholders.length) {
                    String target = "{" + placeholders[i].toString() + "}";
                    String replacement = placeholders[i + 1] != null ? placeholders[i + 1].toString() : "null";
                    msg = msg.replace(target, replacement);
                }
            }
        }

        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
