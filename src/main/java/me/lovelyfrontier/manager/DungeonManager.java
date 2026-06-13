package me.lovelyfrontier.manager;

import me.lovelyfrontier.LovelyFrontierPlugin;
import me.lovelyfrontier.model.DungeonConfig;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DungeonManager {

    private final LovelyFrontierPlugin plugin;
    private final Map<String, DungeonConfig> dungeons = new ConcurrentHashMap<>();

    public DungeonManager(LovelyFrontierPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Scans and loads all dungeon configuration YAML files from the dungeons/ folder.
     */
    public void reload() {
        dungeons.clear();
        File folder = new File(plugin.getDataFolder(), "dungeons");
        if (!folder.exists()) {
            folder.mkdirs();
            // Extract the default ancient_ruins.yml
            plugin.saveResource("dungeons/ancient_ruins.yml", false);
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                String id = config.getString("id");
                if (id == null) {
                    id = file.getName().replace(".yml", "");
                }
                String name = config.getString("name", id);
                String schematicPath = config.getString("schematic_path", id + ".schem");
                int minPartySize = config.getInt("min_party_size", 1);
                int timeLimit = config.getInt("time_limit_seconds", 1800);

                double spawnX = config.getDouble("spawn.x", 0.5);
                double spawnY = config.getDouble("spawn.y", 64.0);
                double spawnZ = config.getDouble("spawn.z", 0.5);
                float spawnYaw = (float) config.getDouble("spawn.yaw", 0.0);
                float spawnPitch = (float) config.getDouble("spawn.pitch", 0.0);

                DungeonConfig dungeon = new DungeonConfig(id, name, schematicPath, minPartySize, timeLimit,
                        spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
                dungeon.setPasteOriginX(config.getInt("paste_origin.x", 0));
                dungeon.setPasteOriginY(config.getInt("paste_origin.y", 4));
                dungeon.setPasteOriginZ(config.getInt("paste_origin.z", 0));
                dungeon.setConfigFile(file);
                dungeons.put(id, dungeon);
                
                // Keep the DB in sync with our loaded configurations (Rule R-010)
                plugin.getDungeonRepository().saveDungeon(dungeon);

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load dungeon file " + file.getName() + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + dungeons.size() + " dungeons from configuration.");
    }

    public DungeonConfig getDungeon(String id) {
        return dungeons.get(id);
    }

    public Collection<DungeonConfig> getAllDungeons() {
        return Collections.unmodifiableCollection(dungeons.values());
    }
}
