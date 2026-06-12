package me.lovelyfrontier.manager;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.PortalType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PortalManager {

    private final LovelyFrontierPlugin plugin;
    private final Map<String, PortalType> portalTypes = new ConcurrentHashMap<>();

    public PortalManager(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
        loadPortalTypes();
    }

    /**
     * Loads all portal type configurations from portal_types.yml.
     */
    public void loadPortalTypes() {
        File file = new File(plugin.getDataFolder(), "portal_types.yml");
        if (!file.exists()) {
            plugin.saveResource("portal_types.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        portalTypes.clear();

        if (config.getConfigurationSection("portal_types") == null) {
            return;
        }

        for (String key : config.getConfigurationSection("portal_types").getKeys(false)) {
            String path = "portal_types." + key + ".";
            String triggerItemStr = config.getString(path + "trigger_item");
            Material triggerItem = Material.matchMaterial(triggerItemStr != null ? triggerItemStr : "");
            
            java.util.List<String> patternList = config.getStringList(path + "pattern");
            String[] pattern = patternList.toArray(new String[0]);

            Map<Character, Material> keys = new HashMap<>();
            if (config.getConfigurationSection(path + "keys") != null) {
                for (String charKey : config.getConfigurationSection(path + "keys").getKeys(false)) {
                    if (!charKey.isEmpty()) {
                        char ch = charKey.charAt(0);
                        String matStr = config.getString(path + "keys." + charKey);
                        Material mat = Material.matchMaterial(matStr != null ? matStr : "");
                        if (mat != null) {
                            keys.put(ch, mat);
                        }
                    }
                }
            }

            String action = config.getString(path + "action");
            String triggerMethod = config.getString(path + "trigger_method", "DROP");

            if (triggerItem != null && pattern.length == 3 && action != null) {
                PortalType portal = new PortalType(key, triggerItem, pattern, keys, action, triggerMethod);
                portalTypes.put(key, portal);
            } else {
                plugin.getLogger().warning("Failed to load portal type: " + key + " (invalid trigger, pattern, or action)");
            }
        }
        plugin.getLogger().info("Loaded " + portalTypes.size() + " portal types.");
    }

    public PortalType getPortalType(String id) {
        return portalTypes.get(id);
    }

    public Map<String, PortalType> getPortalTypes() {
        return portalTypes;
    }

    /**
     * Detects if a grid of block materials matches any registered portal type.
     * The grid cache maps "dx,dz" offsets from the drop location to their Material.
     */
    public CompletableFuture<PortalType> detectPattern(Map<String, Material> blockCache) {
        return CompletableFuture.supplyAsync(() -> {
            for (PortalType type : portalTypes.values()) {
                String[] pattern = type.getPattern();
                if (pattern == null || pattern.length == 0) continue;
                int rows = pattern.length;
                int cols = pattern[0].length();

                int startX = - (rows - 1) / 2;
                int startZ = - (cols - 1) / 2;

                boolean match = true;
                for (int r = 0; r < rows; r++) {
                    String rowStr = pattern[r];
                    if (rowStr == null) {
                        match = false;
                        break;
                    }
                    for (int c = 0; c < Math.min(rowStr.length(), cols); c++) {
                        char ch = rowStr.charAt(c);
                        Material required = type.getPatternKeys().get(ch);
                        if (ch == ' ' || required == null) {
                            continue;
                        }
                        
                        int dx = startX + r;
                        int dz = startZ + c;
                        Material blockType = blockCache.get(dx + "," + dz);
                        if (blockType != required) {
                            match = false;
                            break;
                        }
                    }
                    if (!match) break;
                }

                if (match) {
                    return type;
                }
            }
            return null;
        });
    }
}
