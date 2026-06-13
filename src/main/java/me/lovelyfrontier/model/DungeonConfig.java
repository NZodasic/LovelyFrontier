package me.lovelyfrontier.model;

public class DungeonConfig {
    private String id;
    private String name;
    private String schematicPath;
    private int minPartySize;
    private int timeLimitSeconds;

    // Spawn location parameters (for the instance world, relative or absolute)
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private float spawnYaw;
    private float spawnPitch;

    public DungeonConfig() {}

    public DungeonConfig(String id, String name, String schematicPath, int minPartySize, int timeLimitSeconds,
                         double spawnX, double spawnY, double spawnZ, float spawnYaw, float spawnPitch) {
        this.id = id;
        this.name = name;
        this.schematicPath = schematicPath;
        this.minPartySize = minPartySize;
        this.timeLimitSeconds = timeLimitSeconds;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.spawnYaw = spawnYaw;
        this.spawnPitch = spawnPitch;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSchematicPath() { return schematicPath; }
    public void setSchematicPath(String schematicPath) { this.schematicPath = schematicPath; }

    public int getMinPartySize() { return minPartySize; }
    public void setMinPartySize(int minPartySize) { this.minPartySize = minPartySize; }

    public int getTimeLimitSeconds() { return timeLimitSeconds; }
    public void setTimeLimitSeconds(int timeLimitSeconds) { this.timeLimitSeconds = timeLimitSeconds; }

    public double getSpawnX() { return spawnX; }
    public void setSpawnX(double spawnX) { this.spawnX = spawnX; }

    public double getSpawnY() { return spawnY; }
    public void setSpawnY(double spawnY) { this.spawnY = spawnY; }

    public double getSpawnZ() { return spawnZ; }
    public void setSpawnZ(double spawnZ) { this.spawnZ = spawnZ; }

    public float getSpawnYaw() { return spawnYaw; }
    public void setSpawnYaw(float spawnYaw) { this.spawnYaw = spawnYaw; }

    private transient java.io.File configFile;

    public java.io.File getConfigFile() { return configFile; }
    public void setConfigFile(java.io.File configFile) { this.configFile = configFile; }

    public float getSpawnPitch() { return spawnPitch; }
    public void setSpawnPitch(float spawnPitch) { this.spawnPitch = spawnPitch; }

    // Paste origin coordinates (default to 0, 4, 0 for flat worlds)
    private int pasteOriginX = 0;
    private int pasteOriginY = 4;
    private int pasteOriginZ = 0;

    public int getPasteOriginX() { return pasteOriginX; }
    public void setPasteOriginX(int pasteOriginX) { this.pasteOriginX = pasteOriginX; }

    public int getPasteOriginY() { return pasteOriginY; }
    public void setPasteOriginY(int pasteOriginY) { this.pasteOriginY = pasteOriginY; }

    public int getPasteOriginZ() { return pasteOriginZ; }
    public void setPasteOriginZ(int pasteOriginZ) { this.pasteOriginZ = pasteOriginZ; }
}
