package com.dracave.tags.migrate.core;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SourceReader {
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
        }
    }

    private final String url;
    private final String user;
    private final String password;

    public SourceReader(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static boolean tableExists(Connection c, String tableName) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (tableName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean columnExists(Connection c, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = c.getMetaData().getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    public List<TitleData.SourceTitle> readTitles() throws SQLException {
        List<TitleData.SourceTitle> list = new ArrayList<>();
        try (Connection c = open()) {
            if (!tableExists(c, "title_list")) {
                return list;
            }
            boolean hasIconCol = columnExists(c, "title_list", "icon");
            boolean hasMaterialCol = columnExists(c, "title_list", "material");
            String sql = "SELECT id,title_name,buy_type,amount,day,item_stack,is_hide,description"
                    + (hasIconCol ? ",icon" : "")
                    + (hasMaterialCol ? ",material" : "")
                    + " FROM title_list";
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idx = 1;
                    long id = rs.getLong(idx++);
                    String titleName = nz(rs.getString(idx++));
                    String buyType = nz(rs.getString(idx++)).toLowerCase(Locale.ROOT);
                    long amount = rs.getLong(idx++);
                    int day = rs.getInt(idx++);
                    String itemStack = rs.getString(idx++);
                    boolean isHide = rs.getInt(idx++) != 0;
                    String description = rs.getString(idx++);
                    String icon = null;
                    if (hasIconCol) {
                        icon = rs.getString(idx++);
                    }
                    if (hasMaterialCol && icon == null) {
                        icon = rs.getString(idx++);
                    }
                    list.add(new TitleData.SourceTitle(
                            id, titleName, buyType, amount, day, itemStack, isHide, description, icon));
                }
            }
        }
        return list;
    }

    public List<TitleData.OwnedTitle> readOwned() throws SQLException {
        List<TitleData.OwnedTitle> list = new ArrayList<>();
        try (Connection c = open()) {
            if (!tableExists(c, "title_player")) {
                return list;
            }
            boolean hasUuidCol = columnExists(c, "title_player", "player_uuid");
            String sql = hasUuidCol
                    ? "SELECT id,player_name,player_uuid,title_id,title_name,expiration_time,is_use FROM title_player"
                    : "SELECT id,player_name,title_id,title_name,expiration_time,is_use FROM title_player";
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idx = 1;
                    long id = rs.getLong(idx++);
                    String playerName = nz(rs.getString(idx++));
                    String playerUuid = hasUuidCol ? nz(rs.getString(idx++)) : "";
                    long titleId = rs.getLong(idx++);
                    String titleName = nz(rs.getString(idx++));
                    long expiration = parseExpiration(rs.getString(idx++));
                    boolean isUse = rs.getInt(idx++) != 0;
                    list.add(new TitleData.OwnedTitle(id, playerName, playerUuid, titleId,
                            titleName, expiration, isUse));
                }
            }
        }
        return list;
    }

    public List<TitleData.TitleBuff> readBuffs() throws SQLException {
        List<TitleData.TitleBuff> list = new ArrayList<>();
        try (Connection c = open()) {
            if (!tableExists(c, "title_buff")) {
                return list;
            }
            try (PreparedStatement ps = c.prepareStatement(
                     "SELECT id,title_id,buff_type,buff_content FROM title_buff");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String buffType = nz(rs.getString(3));
                    if (!"potion_effect".equalsIgnoreCase(buffType)) {
                        continue;
                    }
                    String[] parsed = parsePotionJson(rs.getString(4));
                    if (parsed == null) {
                        continue;
                    }
                    list.add(new TitleData.TitleBuff(rs.getLong(1), rs.getLong(2), buffType, parsed[0],
                            Integer.parseInt(parsed[1])));
                }
            }
        }
        return list;
    }

    public List<TitleData.CustomQuota> readQuotas() throws SQLException {
        List<TitleData.CustomQuota> list = new ArrayList<>();
        try (Connection c = open()) {
            if (!tableExists(c, "title_custom")) {
                return list;
            }
            try (PreparedStatement ps = c.prepareStatement(
                     "SELECT player_name,num,use_num FROM title_custom");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new TitleData.CustomQuota(rs.getString(1), rs.getInt(2), rs.getInt(3)));
                }
            }
        }
        return list;
    }

    public List<TitleData.CoinRow> readCoins() throws SQLException {
        List<TitleData.CoinRow> list = new ArrayList<>();
        try (Connection c = open()) {
            if (!tableExists(c, "title_coin")) {
                return list;
            }
            boolean hasUuidCol = columnExists(c, "title_coin", "player_uuid");
            String sql = hasUuidCol
                    ? "SELECT player_name,player_uuid,amount FROM title_coin"
                    : "SELECT player_name,amount FROM title_coin";
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idx = 1;
                    String playerName = nz(rs.getString(idx++));
                    String playerUuid = hasUuidCol ? nz(rs.getString(idx++)) : "";
                    long amount = rs.getLong(idx++);
                    list.add(new TitleData.CoinRow(playerName, playerUuid, amount));
                }
            }
        }
        return list;
    }

    public Map<String, String> readSourceUuids() throws SQLException {
        Map<String, String> uuids = new HashMap<>();
        try (Connection c = open()) {
            for (String table : new String[]{"title_player", "title_coin"}) {
                if (!tableExists(c, table) || !columnExists(c, table, "player_uuid")) {
                    continue;
                }
                try (PreparedStatement ps = c.prepareStatement(
                         "SELECT player_name,player_uuid FROM " + table);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        String uuid = rs.getString(2);
                        if (name != null && !name.isBlank()
                                && uuid != null && !uuid.isBlank() && uuid.length() == 36) {
                            uuids.putIfAbsent(name, uuid);
                        }
                    }
                }
            }
        }
        return uuids;
    }

    static long parseExpiration(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String t = raw.trim();
        if (t.matches("\\d{12,13}")) {
            long v = Long.parseLong(t);
            return v < 100_000_000_000L ? v * 1000L : v;
        }
        if (t.matches("\\d{10}")) {
            return Long.parseLong(t) * 1000L;
        }
        for (String pattern : new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"}) {
            try {
                return new SimpleDateFormat(pattern, Locale.ROOT).parse(t).getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    static String[] parsePotionJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String name = null;
        int level = 1;
        for (String part : json.split("[{},]")) {
            String kv = part.trim();
            if (kv.startsWith("\"potionName\"")) {
                int eq = kv.indexOf(':');
                if (eq < 0) {
                    continue;
                }
                name = kv.substring(eq + 1).trim().replace("\"", "");
            } else if (kv.startsWith("\"potionLevel\"")) {
                int eq = kv.indexOf(':');
                if (eq < 0) {
                    continue;
                }
                String v = kv.substring(eq + 1).trim().replace("\"", "");
                try {
                    level = Integer.parseInt(v);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return name == null || name.isBlank() ? null : new String[]{name, String.valueOf(level)};
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
