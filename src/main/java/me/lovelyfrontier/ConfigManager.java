package me.lovelyfrontier;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class ConfigManager {

    private final JavaPlugin plugin;
    private YamlConfiguration config;

    // Party config
    private int partyMinSize;
    private boolean partyAllowSolo;
    private int partyConfirmTimeout;
    private int partyVoteTimeout;
    private double partyRewardSolo;
    private double partyRewardDuo;
    private double partyRewardTrio;

    // Tickets config
    private boolean ticketsConsumeOnEntry;
    private boolean ticketsUniversalTicket;

    // Instance config
    private int instanceDisconnectGrace;
    private int instanceLoadingTimeout;
    private int instanceMaxActive;

    // Portal Scroll config
    private int portalScrollDailyLimit;
    private int portalScrollMaxHold;
    private int portalScrollCooldown;

    // Anti-Abuse config
    private String antiAbuseMinPlaytime;
    private double antiAbuseMinPlaytimeHours;
    private int antiAbuseMaxAccountsPerIp;

    // Weekly Free Tickets config
    private boolean weeklyFreeTicketsEnabled;
    private int weeklyFreeTicketsAmount;

    // Command Filter config
    private java.util.List<String> commandFilterAllowedCommands;

    // World Dungeon Spawn config
    private boolean worldDungeonSpawnEnabled;
    private String worldDungeonSpawnNotificationMode;
    private int worldDungeonSpawnCheckInterval;
    private int worldDungeonSpawnPortalLifetime;

    // Shop config
    private double shopBasePrice;
    private final java.util.Map<String, Double> shopMultipliers = new java.util.HashMap<>();

    // Database config
    private String dbType;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private int mysqlPoolMaxSize;
    private long mysqlPoolIdleTimeout;
    private long mysqlPoolConnectionTimeout;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        // Load Party Section
        this.partyMinSize = config.getInt("party.min_size", 1);
        this.partyAllowSolo = config.getBoolean("party.allow_solo", true);
        this.partyConfirmTimeout = config.getInt("party.confirm_timeout_seconds", 180);
        this.partyVoteTimeout = config.getInt("party.vote_timeout_seconds", 60);
        this.partyRewardSolo = config.getDouble("party.reward_scaling.solo", 0.70);
        this.partyRewardDuo = config.getDouble("party.reward_scaling.duo", 1.00);
        this.partyRewardTrio = config.getDouble("party.reward_scaling.trio", 1.10);

        // Load Tickets Section
        this.ticketsConsumeOnEntry = config.getBoolean("tickets.consume_on_entry", true);
        this.ticketsUniversalTicket = config.getBoolean("tickets.universal_ticket", false);

        // Load Instance Section
        this.instanceDisconnectGrace = config.getInt("instance.disconnect_grace_seconds", 300);
        this.instanceLoadingTimeout = config.getInt("instance.loading_timeout_seconds", 60);
        this.instanceMaxActive = config.getInt("instance.max_active_instances", 50);

        // Load Portal Scroll Section
        this.portalScrollDailyLimit = config.getInt("portal_scroll.daily_limit", 3);
        this.portalScrollMaxHold = config.getInt("portal_scroll.max_hold", 5);
        this.portalScrollCooldown = config.getInt("portal_scroll.cooldown_between_uses_seconds", 300);

        // Load Anti-Abuse Section
        if (config.contains("anti_abuse.min_playtime")) {
            this.antiAbuseMinPlaytime = config.getString("anti_abuse.min_playtime", "2h");
            this.antiAbuseMinPlaytimeHours = parseTimeToHours(antiAbuseMinPlaytime);
        } else {
            int oldHours = config.getInt("anti_abuse.min_playtime_hours", 2);
            this.antiAbuseMinPlaytime = oldHours + "h";
            this.antiAbuseMinPlaytimeHours = (double) oldHours;
        }
        this.antiAbuseMaxAccountsPerIp = config.getInt("anti_abuse.max_accounts_per_ip", 3);

        // Load Weekly Free Tickets Section
        this.weeklyFreeTicketsEnabled = config.getBoolean("weekly_free_tickets.enabled", true);
        this.weeklyFreeTicketsAmount = config.getInt("weekly_free_tickets.amount", 1);

        // Load Command Filter Section
        this.commandFilterAllowedCommands = config.getStringList("command_filter.allowed_commands");
        if (this.commandFilterAllowedCommands == null || this.commandFilterAllowedCommands.isEmpty()) {
            this.commandFilterAllowedCommands = java.util.Arrays.asList("lf", "lfa", "party", "me", "g", "msg");
        }

        // Load World Dungeon Spawn Section
        this.worldDungeonSpawnEnabled = config.getBoolean("world_dungeon_spawn.enabled", true);
        this.worldDungeonSpawnNotificationMode = config.getString("world_dungeon_spawn.notification.mode", "BOTH");
        this.worldDungeonSpawnCheckInterval = config.getInt("world_dungeon_spawn.check_interval_seconds", 300);
        this.worldDungeonSpawnPortalLifetime = config.getInt("world_dungeon_spawn.portal_lifetime_seconds", 600);

        // Load Database Section
        this.dbType = config.getString("database.type", "sqlite");
        this.mysqlHost = config.getString("database.mysql.host", "localhost");
        this.mysqlPort = config.getInt("database.mysql.port", 3306);
        this.mysqlDatabase = config.getString("database.mysql.database", "lovelyfrontier");
        this.mysqlUsername = config.getString("database.mysql.username", "root");
        this.mysqlPassword = config.getString("database.mysql.password", "");
        this.mysqlPoolMaxSize = config.getInt("database.mysql.pool.max_size", 10);
        this.mysqlPoolIdleTimeout = config.getLong("database.mysql.pool.idle_timeout_ms", 600000L);
        this.mysqlPoolConnectionTimeout = config.getLong("database.mysql.pool.connection_timeout_ms", 30000L);

        // Load Shop Section
        this.shopBasePrice = config.getDouble("tickets_shop.base_price", 1000.0);
        this.shopMultipliers.clear();
        if (config.getConfigurationSection("tickets_shop.multipliers") != null) {
            for (String key : config.getConfigurationSection("tickets_shop.multipliers").getKeys(false)) {
                this.shopMultipliers.put(key.toUpperCase(), config.getDouble("tickets_shop.multipliers." + key, 1.0));
            }
        } else {
            this.shopMultipliers.put("VERY_EASY", 0.8);
            this.shopMultipliers.put("EASY", 1.0);
            this.shopMultipliers.put("NORMAL", 1.3);
            this.shopMultipliers.put("HARD", 1.7);
            this.shopMultipliers.put("VERY_HARD", 2.5);
        }
    }

    public double getShopBasePrice() { return shopBasePrice; }
    public double getShopMultiplier(String difficulty) {
        return shopMultipliers.getOrDefault(difficulty.toUpperCase(), 1.0);
    }
    public java.util.Map<String, Double> getShopMultipliers() { return shopMultipliers; }

    public void saveShopConfig(double basePrice, java.util.Map<String, Double> multipliers) {
        config.set("tickets_shop.base_price", basePrice);
        for (java.util.Map.Entry<String, Double> entry : multipliers.entrySet()) {
            config.set("tickets_shop.multipliers." + entry.getKey().toLowerCase(), entry.getValue());
        }
        try {
            config.save(new File(plugin.getDataFolder(), "config.yml"));
            reload();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save config.yml shop changes: " + e.getMessage());
        }
    }

    public void savePortalLifetime(int seconds) {
        config.set("world_dungeon_spawn.portal_lifetime_seconds", seconds);
        try {
            config.save(new File(plugin.getDataFolder(), "config.yml"));
            reload();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save config.yml portal lifetime changes: " + e.getMessage());
        }
    }

    public int getPartyMinSize() { return partyMinSize; }
    public boolean isPartyAllowSolo() { return partyAllowSolo; }
    public int getPartyConfirmTimeout() { return partyConfirmTimeout; }
    public int getPartyVoteTimeout() { return partyVoteTimeout; }
    public double getPartyRewardSolo() { return partyRewardSolo; }
    public double getPartyRewardDuo() { return partyRewardDuo; }
    public double getPartyRewardTrio() { return partyRewardTrio; }

    public boolean isTicketsConsumeOnEntry() { return ticketsConsumeOnEntry; }
    public boolean isTicketsUniversalTicket() { return ticketsUniversalTicket; }

    public int getInstanceDisconnectGrace() { return instanceDisconnectGrace; }
    public int getInstanceLoadingTimeout() { return instanceLoadingTimeout; }
    public int getInstanceMaxActive() { return instanceMaxActive; }

    public int getPortalScrollDailyLimit() { return portalScrollDailyLimit; }
    public int getPortalScrollMaxHold() { return portalScrollMaxHold; }
    public int getPortalScrollCooldown() { return portalScrollCooldown; }

    public String getAntiAbuseMinPlaytime() { return antiAbuseMinPlaytime; }
    public double getAntiAbuseMinPlaytimeHours() { return antiAbuseMinPlaytimeHours; }
    public int getAntiAbuseMaxAccountsPerIp() { return antiAbuseMaxAccountsPerIp; }

    public static double parseTimeToHours(String input) {
        if (input == null || input.trim().isEmpty()) {
            return 0.0;
        }
        input = input.trim().toLowerCase();
        
        double multiplier = 1.0; // default to hours
        String numStr = input;
        
        if (input.endsWith("d")) {
            multiplier = 24.0;
            numStr = input.substring(0, input.length() - 1);
        } else if (input.endsWith("h")) {
            multiplier = 1.0;
            numStr = input.substring(0, input.length() - 1);
        } else if (input.endsWith("m")) {
            multiplier = 1.0 / 60.0;
            numStr = input.substring(0, input.length() - 1);
        } else if (input.endsWith("s")) {
            multiplier = 1.0 / 3600.0;
            numStr = input.substring(0, input.length() - 1);
        }
        
        try {
            return Double.parseDouble(numStr.trim()) * multiplier;
        } catch (NumberFormatException e) {
            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException ex) {
                return 0.0;
            }
        }
    }

    public static String formatPlaytime(double hours) {
        if (hours >= 24) {
            return String.format(java.util.Locale.US, "%.1fd", hours / 24.0);
        } else if (hours >= 1) {
            return String.format(java.util.Locale.US, "%.1fh", hours);
        } else if (hours >= 1.0 / 60.0) {
            return String.format(java.util.Locale.US, "%.1fm", hours * 60.0);
        } else {
            return String.format(java.util.Locale.US, "%.1fs", hours * 3600.0);
        }
    }

    public boolean isWorldDungeonSpawnEnabled() { return worldDungeonSpawnEnabled; }
    public String getWorldDungeonSpawnNotificationMode() { return worldDungeonSpawnNotificationMode; }
    public int getWorldDungeonSpawnCheckInterval() { return worldDungeonSpawnCheckInterval; }
    public int getWorldDungeonSpawnPortalLifetime() { return worldDungeonSpawnPortalLifetime; }

    public boolean isWeeklyFreeTicketsEnabled() { return weeklyFreeTicketsEnabled; }
    public int getWeeklyFreeTicketsAmount() { return weeklyFreeTicketsAmount; }
    public java.util.List<String> getCommandFilterAllowedCommands() { return commandFilterAllowedCommands; }

    public String getDbType() { return dbType; }
    public String getMysqlHost() { return mysqlHost; }
    public int getMysqlPort() { return mysqlPort; }
    public String getMysqlDatabase() { return mysqlDatabase; }
    public String getMysqlUsername() { return mysqlUsername; }
    public String getMysqlPassword() { return mysqlPassword; }
    public int getMysqlPoolMaxSize() { return mysqlPoolMaxSize; }
    public long getMysqlPoolIdleTimeout() { return mysqlPoolIdleTimeout; }
    public long getMysqlPoolConnectionTimeout() { return mysqlPoolConnectionTimeout; }
}
