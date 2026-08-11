package com.dracave.tags.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;

public final class DbPool implements AutoCloseable {
    private final HikariDataSource source;
    private final boolean sqliteMode;
    private final String playerTbl;
    private final String unlockTbl;
    private final String purchaseTbl;
    private final String customTbl;
    private final String schemaTbl;
    private final String defTbl;
    private final String descTbl;
    private final String colorTbl;
    private final String effectTbl;
    private final String frameTbl;
    private final String coinTbl;
    private final String quotaTbl;
    private final String rewardTbl;
    private final String rewardLogTbl;

    public DbPool(FileConfiguration config, File dataFolder) throws Exception {
        String prefix = sanitize(config.getString("database.mysql.table-prefix", "dracavetags_"));
        playerTbl = prefix + "player";
        unlockTbl = prefix + "unlock";
        purchaseTbl = prefix + "purchase";
        customTbl = prefix + "custom_tag";
        schemaTbl = prefix + "schema_version";
        defTbl = prefix + "tag_definition";
        descTbl = prefix + "tag_description";
        colorTbl = prefix + "tag_color";
        effectTbl = prefix + "tag_effect";
        frameTbl = prefix + "tag_frame";
        coinTbl = prefix + "coin";
        quotaTbl = prefix + "quota";
        rewardTbl = prefix + "reward";
        rewardLogTbl = prefix + "reward_log";

        sqliteMode = config.getString("database.type", "MYSQL").equalsIgnoreCase("SQLITE");
        HikariConfig hikari = new HikariConfig();
        if (sqliteMode) {
            File file = new File(dataFolder, config.getString("database.sqlite.file", "data.db"));
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath() + "?busy_timeout=5000");
            hikari.setMaximumPoolSize(1);
        } else {
            String host = config.getString("database.mysql.host", "127.0.0.1");
            int port = config.getInt("database.mysql.port", 3306);
            String database = config.getString("database.mysql.database", "minecraft");
            String params = config.getString("database.mysql.params",
                    "?useSSL=false&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true");
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + params);
            hikari.setUsername(config.getString("database.mysql.user", "root"));
            hikari.setPassword(config.getString("database.mysql.password", ""));
            hikari.setMaximumPoolSize(Math.max(1, config.getInt("database.mysql.pool-size", 4)));
        }

        String driverClass = sqliteMode ? "org.sqlite.JDBC" : "com.mysql.cj.jdbc.Driver";
        String[] candidates = sqliteMode
                ? new String[]{driverClass, "com.dracave.tags.libs.sqlite.JDBC"}
                : new String[]{driverClass, "com.dracave.tags.libs.mysql.cj.jdbc.Driver"};
        for (String candidate : candidates) {
            try {
                Class.forName(candidate);
                hikari.setDriverClassName(candidate);
                break;
            } catch (ClassNotFoundException ignored) {
            }
        }

        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(10000L);
        hikari.setPoolName(sqliteMode ? "DraCaveTags-SQLite" : "DraCaveTags-MySQL");
        source = new HikariDataSource(hikari);

        try {
            initializeSchema();
        } catch (Exception ex) {
            source.close();
            throw ex;
        }
    }

    public HikariDataSource source() {
        return source;
    }

    public boolean sqliteMode() {
        return sqliteMode;
    }

    public String playerTbl() { return playerTbl; }
    public String unlockTbl() { return unlockTbl; }
    public String purchaseTbl() { return purchaseTbl; }
    public String customTbl() { return customTbl; }
    public String schemaTbl() { return schemaTbl; }
    public String defTbl() { return defTbl; }
    public String descTbl() { return descTbl; }
    public String colorTbl() { return colorTbl; }
    public String effectTbl() { return effectTbl; }
    public String frameTbl() { return frameTbl; }
    public String coinTbl() { return coinTbl; }
    public String quotaTbl() { return quotaTbl; }
    public String rewardTbl() { return rewardTbl; }
    public String rewardLogTbl() { return rewardLogTbl; }

    public String engineSuffix() {
        return sqliteMode ? "" : " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    private void initializeSchema() throws Exception {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + schemaTbl
                    + "` (version INT NOT NULL PRIMARY KEY, applied_at BIGINT NOT NULL)" + engineSuffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + defTbl
                    + "` (tag_id VARCHAR(64) NOT NULL PRIMARY KEY, tag_text VARCHAR(128) NOT NULL,"
                    + " icon VARCHAR(64) NOT NULL, sort_order INT NOT NULL, default_unlocked BOOLEAN NOT NULL,"
                    + " permission_node VARCHAR(255) NULL, shop_hidden BOOLEAN NOT NULL DEFAULT TRUE,"
                    + " shop_currency VARCHAR(32) NULL, shop_price DECIMAL(19,4) NULL,"
                    + " gradient_period_ticks INT NULL, animation_type VARCHAR(24) NULL,"
                    + " particle_type VARCHAR(64) NULL, particle_id VARCHAR(64) NULL,"
                    + " particle_colors VARCHAR(255) NULL, revision INT NOT NULL, created_at BIGINT NOT NULL,"
                    + " updated_at BIGINT NOT NULL)" + engineSuffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + descTbl
                    + "` (tag_id VARCHAR(64) NOT NULL, position INT NOT NULL, description_text TEXT NOT NULL,"
                    + " PRIMARY KEY(tag_id,position))" + engineSuffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + colorTbl
                    + "` (tag_id VARCHAR(64) NOT NULL, position INT NOT NULL, color VARCHAR(7) NOT NULL,"
                    + " PRIMARY KEY(tag_id,position))" + engineSuffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + effectTbl
                    + "` (tag_id VARCHAR(64) NOT NULL, position INT NOT NULL, effect_type VARCHAR(64) NOT NULL,"
                    + " effect_level INT NOT NULL, PRIMARY KEY(tag_id,position), UNIQUE(tag_id,effect_type))" + engineSuffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + frameTbl
                    + "` (tag_id VARCHAR(64) NOT NULL, position INT NOT NULL, frame_text VARCHAR(128) NOT NULL,"
                    + " PRIMARY KEY(tag_id,position))" + engineSuffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + playerTbl
                    + "` (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, equipped_id VARCHAR(64) NULL,"
                    + " updated_at BIGINT NOT NULL)" + engineSuffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + unlockTbl
                    + "` (player_uuid VARCHAR(36) NOT NULL, tag_id VARCHAR(64) NOT NULL,"
                    + " unlocked_at BIGINT NOT NULL, expires_at BIGINT NULL,"
                    + " PRIMARY KEY(player_uuid,tag_id))" + engineSuffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + purchaseTbl
                    + "` (operation_id VARCHAR(36) NOT NULL PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL,"
                    + " tag_id VARCHAR(64) NOT NULL, currency VARCHAR(32) NOT NULL, amount DECIMAL(19,4) NOT NULL,"
                    + " state VARCHAR(24) NOT NULL, failure_reason VARCHAR(255) NULL, refunded BOOLEAN NOT NULL DEFAULT FALSE,"
                    + " created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, completed_at BIGINT NULL)" + engineSuffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + customTbl
                    + "` (tag_id VARCHAR(64) NOT NULL PRIMARY KEY, owner_uuid VARCHAR(36) NOT NULL,"
                    + " tag_text VARCHAR(128) NOT NULL, type VARCHAR(24) NOT NULL, colors VARCHAR(2048) NOT NULL,"
                    + " frames VARCHAR(4096) NOT NULL, period_ticks INT NOT NULL, icon VARCHAR(64) NOT NULL,"
                    + " status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', revision INT NOT NULL,"
                    + " created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, deleted_at BIGINT NULL)" + engineSuffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + coinTbl
                    + "` (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, amount BIGINT NOT NULL DEFAULT 0,"
                    + " updated_at BIGINT NOT NULL)" + engineSuffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + quotaTbl
                    + "` (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, quota INT NOT NULL DEFAULT 0,"
                    + " updated_at BIGINT NOT NULL)" + engineSuffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + rewardTbl
                    + "` (id BIGINT NOT NULL PRIMARY KEY, number INT NOT NULL, reward_kind VARCHAR(32) NOT NULL,"
                    + " amount BIGINT NOT NULL)" + engineSuffix());
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + rewardLogTbl
                    + "` (id BIGINT NOT NULL PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL,"
                    + " reward_id BIGINT NOT NULL, claimed_at BIGINT NOT NULL)" + engineSuffix());
        }
    }

    private static String sanitize(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "dracavetags_";
        }
        return prefix.replaceAll("[^A-Za-z0-9_]", "");
    }

    @Override
    public void close() {
        source.close();
    }
}
