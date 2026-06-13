package me.lovelyfrontier.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.lovelyfrontier.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private HikariDataSource dataSource;
    private boolean isMySQL;

    public DatabaseManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        initConnectionPool();
        initTables();
    }

    private void initConnectionPool() {
        HikariConfig config = new HikariConfig();
        String type = configManager.getDbType().toLowerCase();

        if (type.equals("mysql")) {
            isMySQL = true;
            config.setJdbcUrl("jdbc:mysql://" + configManager.getMysqlHost() + ":" + configManager.getMysqlPort() + "/" + configManager.getMysqlDatabase() + "?useSSL=false&allowPublicKeyRetrieval=true");
            config.setUsername(configManager.getMysqlUsername());
            config.setPassword(configManager.getMysqlPassword());
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setMaximumPoolSize(configManager.getMysqlPoolMaxSize());
            config.setIdleTimeout(configManager.getMysqlPoolIdleTimeout());
            config.setConnectionTimeout(configManager.getMysqlPoolConnectionTimeout());
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        } else {
            isMySQL = false;
            File dbFile = new File(plugin.getDataFolder(), "lovelyfrontier.db");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1); // SQLite is single-write, keep pool small
            config.setConnectionTimeout(30000);
        }

        config.setPoolName("LovelyFrontierPool");
        this.dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is not initialized.");
        }
        return dataSource.getConnection();
    }

    public boolean isMySQL() {
        return isMySQL;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private void initTables() {
        String[] queries = {
            // 1. lf_player_profile
            "CREATE TABLE IF NOT EXISTS lf_player_profile (" +
            "  player_uuid VARCHAR(36) PRIMARY KEY," +
            "  ip_hash VARCHAR(64) NOT NULL," +
            "  total_playtime_h DOUBLE DEFAULT 0.0," +
            "  flagged BOOLEAN DEFAULT FALSE," +
            "  last_free_ticket_at TIMESTAMP NULL," +
            "  last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")",

            // 2. lf_tickets
            "CREATE TABLE IF NOT EXISTS lf_tickets (" +
            "  ticket_id VARCHAR(36) PRIMARY KEY," +
            "  owner_uuid VARCHAR(36) NOT NULL," +
            "  dungeon_id VARCHAR(64) NOT NULL," +
            "  difficulty VARCHAR(32) NOT NULL," +
            "  quantity INT DEFAULT 1," +
            "  UNIQUE(owner_uuid, dungeon_id, difficulty)" +
            ")",

            // 3. lf_sessions
            "CREATE TABLE IF NOT EXISTS lf_sessions (" +
            "  session_id VARCHAR(36) PRIMARY KEY," +
            "  idempotency_key VARCHAR(36) UNIQUE," +
            "  dungeon_id VARCHAR(64) NOT NULL," +
            "  difficulty VARCHAR(32) NOT NULL," +
            "  leader_uuid VARCHAR(36) NOT NULL," +
            "  party_members TEXT NOT NULL," +
            "  ticket_consumed BOOLEAN DEFAULT FALSE," +
            "  instance_id VARCHAR(36) NULL," +
            "  expires_at TIMESTAMP NOT NULL" +
            ")",

            // 4. lf_instances
            "CREATE TABLE IF NOT EXISTS lf_instances (" +
            "  instance_id VARCHAR(36) PRIMARY KEY," +
            "  dungeon_id VARCHAR(64) NOT NULL," +
            "  difficulty VARCHAR(32) NOT NULL," +
            "  state VARCHAR(32) NOT NULL," +
            "  boss_cleared BOOLEAN DEFAULT FALSE," +
            "  boss_hp_percent DOUBLE DEFAULT 1.0," +
            "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")",

            // 5. lf_world_portals
            "CREATE TABLE IF NOT EXISTS lf_world_portals (" +
            "  portal_id VARCHAR(36) PRIMARY KEY," +
            "  dungeon_id VARCHAR(64) NOT NULL," +
            "  world_name VARCHAR(64) NOT NULL," +
            "  x DOUBLE NOT NULL," +
            "  y DOUBLE NOT NULL," +
            "  z DOUBLE NOT NULL," +
            "  locked_by VARCHAR(36) NULL," +
            "  lock_version INT DEFAULT 0," +
            "  expires_at TIMESTAMP NOT NULL" +
            ")",

            // 6. lf_dungeons
            "CREATE TABLE IF NOT EXISTS lf_dungeons (" +
            "  dungeon_id VARCHAR(64) PRIMARY KEY," +
            "  name VARCHAR(64) NOT NULL," +
            "  schematic_path VARCHAR(256) NOT NULL," +
            "  min_party_size INT NOT NULL," +
            "  time_limit_seconds INT NOT NULL," +
            "  spawn_x DOUBLE NULL," +
            "  spawn_y DOUBLE NULL," +
            "  spawn_z DOUBLE NULL," +
            "  spawn_yaw FLOAT NULL," +
            "  spawn_pitch FLOAT NULL," +
            "  enabled BOOLEAN DEFAULT TRUE" +
            ")",

            // 7. lf_instance_members
            "CREATE TABLE IF NOT EXISTS lf_instance_members (" +
            "  instance_id VARCHAR(36) NOT NULL," +
            "  player_uuid VARCHAR(36) NOT NULL," +
            "  connected BOOLEAN DEFAULT TRUE," +
            "  disconnect_at TIMESTAMP NULL," +
            "  PRIMARY KEY(instance_id, player_uuid)" +
            ")",

            // 8. lf_dungeon_chests
            "CREATE TABLE IF NOT EXISTS lf_dungeon_chests (" +
            "  chest_id VARCHAR(36) PRIMARY KEY," +
            "  dungeon_id VARCHAR(64) NOT NULL," +
            "  instance_id VARCHAR(36) NOT NULL," +
            "  world_name VARCHAR(64) NOT NULL," +
            "  x INT NOT NULL," +
            "  y INT NOT NULL," +
            "  z INT NOT NULL," +
            "  loot_pool_id VARCHAR(64) NOT NULL," +
            "  opened BOOLEAN DEFAULT FALSE," +
            "  UNIQUE(dungeon_id, instance_id, world_name, x, y, z)" +
            ")",

            // 9. lf_completions
            "CREATE TABLE IF NOT EXISTS lf_completions (" +
            "  completion_id VARCHAR(36) PRIMARY KEY," +
            "  dungeon_id VARCHAR(64) NOT NULL," +
            "  difficulty VARCHAR(32) NOT NULL," +
            "  party_size INT NOT NULL," +
            "  clear_time_seconds INT NOT NULL," +
            "  completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")",

            // 10. lf_portal_queue
            "CREATE TABLE IF NOT EXISTS lf_portal_queue (" +
            "  queue_id VARCHAR(36) PRIMARY KEY," +
            "  player_uuid VARCHAR(36) NOT NULL," +
            "  dungeon_id VARCHAR(64) NOT NULL," +
            "  queued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")",

            // 11. lf_mail
            "CREATE TABLE IF NOT EXISTS lf_mail (" +
            "  mail_id VARCHAR(36) PRIMARY KEY," +
            "  player_uuid VARCHAR(36) NOT NULL," +
            "  dungeon_id VARCHAR(64) NOT NULL," +
            "  items_json TEXT NOT NULL," +
            "  claimed BOOLEAN DEFAULT FALSE," +
            "  sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")",

            // 12. lf_matchmaking_queue
            "CREATE TABLE IF NOT EXISTS lf_matchmaking_queue (" +
            "  player_uuid VARCHAR(36) PRIMARY KEY," +
            "  dungeon_id VARCHAR(64) NOT NULL," +
            "  difficulty VARCHAR(32) NOT NULL," +
            "  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")",

            // 13. lf_scroll_usage
            "CREATE TABLE IF NOT EXISTS lf_scroll_usage (" +
            "  player_uuid VARCHAR(36) NOT NULL," +
            "  use_date VARCHAR(10) NOT NULL," +
            "  use_count INT DEFAULT 0," +
            "  PRIMARY KEY(player_uuid, use_date)" +
            ")",

            // 14. lf_vote_kick_log
            "CREATE TABLE IF NOT EXISTS lf_vote_kick_log (" +
            "  vote_id VARCHAR(36) PRIMARY KEY," +
            "  instance_id VARCHAR(36) NOT NULL," +
            "  initiator_uuid VARCHAR(36) NOT NULL," +
            "  target_uuid VARCHAR(36) NOT NULL," +
            "  reason VARCHAR(256) NOT NULL," +
            "  votes_yes INT DEFAULT 0," +
            "  votes_no INT DEFAULT 0," +
            "  result VARCHAR(32) NOT NULL," +
            "  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")",

            // 15. lf_saga_log
            "CREATE TABLE IF NOT EXISTS lf_saga_log (" +
            "  saga_id VARCHAR(36) PRIMARY KEY," +
            "  instance_id VARCHAR(36) NOT NULL," +
            "  step_name VARCHAR(64) NOT NULL," +
            "  status VARCHAR(32) NOT NULL," +
            "  error_message TEXT NULL," +
            "  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")"
        };

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (String query : queries) {
                stmt.execute(query);
            }

            // Run migration for lf_player_profile if needed
            boolean hasUuid = false;
            try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "lf_player_profile", "uuid")) {
                if (rs.next()) {
                    hasUuid = true;
                }
            }
            if (hasUuid) {
                try {
                    stmt.execute("ALTER TABLE lf_player_profile RENAME COLUMN uuid TO player_uuid");
                } catch (SQLException e) {
                    try {
                        stmt.execute("ALTER TABLE lf_player_profile CHANGE uuid player_uuid VARCHAR(36)");
                    } catch (SQLException ex) {
                        plugin.getLogger().warning("Could not rename uuid column: " + ex.getMessage());
                    }
                }
            }

            boolean hasFlagged = false;
            try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "lf_player_profile", "flagged")) {
                if (rs.next()) {
                    hasFlagged = true;
                }
            }
            if (!hasFlagged) {
                try {
                    stmt.execute("ALTER TABLE lf_player_profile ADD COLUMN flagged BOOLEAN DEFAULT FALSE");
                } catch (SQLException e) {
                    plugin.getLogger().warning("Could not add flagged column: " + e.getMessage());
                }
            }

            boolean hasLastFreeTicket = false;
            try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "lf_player_profile", "last_free_ticket_at")) {
                if (rs.next()) {
                    hasLastFreeTicket = true;
                }
            }
            if (!hasLastFreeTicket) {
                try {
                    stmt.execute("ALTER TABLE lf_player_profile ADD COLUMN last_free_ticket_at TIMESTAMP NULL");
                } catch (SQLException e) {
                    plugin.getLogger().warning("Could not add last_free_ticket_at column: " + e.getMessage());
                }
            }

            try {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_lf_dungeon_chests_location " +
                        "ON lf_dungeon_chests (dungeon_id, instance_id, world_name, x, y, z)");
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not add dungeon chest location index: " + e.getMessage());
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize database tables", e);
        }
    }
}
