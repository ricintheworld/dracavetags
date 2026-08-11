package com.dracave.tags.migrate.core;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TargetWriter {
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
        }
    }

    private final String url;
    private final String user;
    private final String password;
    private final String p;

    public TargetWriter(String url, String user, String password, String tablePrefix) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.p = tablePrefix;
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public int[] writeDefinitions(List<TitleData.SourceTitle> titles, List<TitleData.TitleBuff> buffs,
                                  Map<Long, String> idMap, MigrateConfig.Mode mode) throws SQLException {
        int inserted = 0;
        int updated = 0;
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                for (TitleData.SourceTitle t : titles) {
                    String newId = idMap.get(t.id());
                    if (newId == null) {
                        continue;
                    }
                    if (exists(c, newId)) {
                        deleteDefinition(c, newId);
                        updated++;
                    } else {
                        inserted++;
                    }
                    insertDefinition(c, newId, t, mode);
                }
                writeBuffs(c, buffs, idMap);
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        }
        return new int[]{inserted, updated};
    }

    private void deleteDefinition(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM `" + p + "tag_definition` WHERE tag_id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
        for (String table : new String[]{"tag_description", "tag_color", "tag_effect", "tag_frame"}) {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM `" + p + table + "` WHERE tag_id=?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            } catch (SQLException ex) {
                if (!ex.getMessage().contains("no such table") && !ex.getMessage().contains("doesn't exist")) {
                    throw ex;
                }
            }
        }
    }

    private boolean exists(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM `" + p + "tag_definition` WHERE tag_id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertDefinition(Connection c, String id, TitleData.SourceTitle t, MigrateConfig.Mode mode) throws SQLException {
        boolean dynamic = mode == MigrateConfig.Mode.DYNAMIC;
        String titleText = dynamic
                ? LegacyParser.parse(t.titleName()).text()
                : LegacyParser.toMiniMessage(t.titleName());
        List<String> colors = dynamic ? LegacyParser.parse(t.titleName()).colors() : List.of();
        String currency = null;
        String permission = "";
        switch (t.buyType()) {
            case "vault" -> currency = "vault";
            case "playerpoints" -> currency = "playerpoints";
            case "coin" -> currency = "coin";
            case "itemstack" -> currency = "item";
            case "permission" -> permission = "dracave.tags.permission." + id;
            default -> {
            }
        }
        boolean hidden = t.isHide() || "not".equals(t.buyType()) || "activity".equals(t.buyType())
                || "permission".equals(t.buyType());
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO `" + p + "tag_definition`"
                        + "(tag_id,tag_text,icon,sort_order,default_unlocked,permission_node,shop_hidden,"
                        + "shop_currency,shop_price,gradient_period_ticks,animation_type,"
                        + "particle_type,particle_id,particle_colors,revision,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, titleText);
            ps.setString(3, "NAME_TAG");
            ps.setInt(4, (int) t.id());
            ps.setBoolean(5, false);
            ps.setString(6, permission);
            ps.setBoolean(7, hidden);
            if (hidden || currency == null || t.amount() <= 0) {
                ps.setNull(8, Types.VARCHAR);
                ps.setNull(9, Types.DECIMAL);
            } else {
                ps.setString(8, currency);
                ps.setBigDecimal(9, BigDecimal.valueOf(t.amount()));
            }
            if (dynamic && colors.size() >= 2) {
                ps.setInt(10, 40);
                ps.setString(11, "FLOWING_GRADIENT");
            } else {
                ps.setNull(10, Types.INTEGER);
                ps.setNull(11, Types.VARCHAR);
            }
            ps.setNull(12, Types.VARCHAR);
            ps.setNull(13, Types.VARCHAR);
            ps.setNull(14, Types.VARCHAR);
            ps.setInt(15, 0);
            long now = System.currentTimeMillis();
            ps.setLong(16, now);
            ps.setLong(17, now);
            ps.executeUpdate();
        }
        if (dynamic && colors.size() >= 2) {
            insertColors(c, id, colors);
        }
        if (t.description() != null && !t.description().isBlank()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO `" + p + "tag_description`(tag_id,position,description_text) VALUES (?,?,?)")) {
                ps.setString(1, id);
                ps.setInt(2, 0);
                ps.setString(3, t.description());
                ps.executeUpdate();
            }
        }
    }

    private void insertColors(Connection c, String id, List<String> colors) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO `" + p + "tag_color`(tag_id,position,color) VALUES (?,?,?)")) {
            int position = 0;
            for (String color : colors) {
                ps.setString(1, id);
                ps.setInt(2, position++);
                ps.setString(3, color);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void writeBuffs(Connection c, List<TitleData.TitleBuff> buffs, Map<Long, String> idMap) throws SQLException {
        Map<String, Integer> positions = new HashMap<>();
        java.util.Set<String> seenEffects = new java.util.HashSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO `" + p + "tag_effect`(tag_id,position,effect_type,effect_level) VALUES (?,?,?,?)")) {
            for (TitleData.TitleBuff buff : buffs) {
                String newId = idMap.get(buff.titleId());
                if (newId == null) {
                    continue;
                }
                String effectKey = newId + ":" + buff.potionName();
                if (!seenEffects.add(effectKey)) {
                    continue;
                }
                int pos = positions.getOrDefault(newId, positionOf(c, newId));
                ps.setString(1, newId);
                ps.setInt(2, pos);
                ps.setString(3, buff.potionName());
                ps.setInt(4, buff.potionLevel());
                ps.addBatch();
                positions.put(newId, pos + 1);
            }
            ps.executeBatch();
        }
    }

    private int positionOf(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM `" + p + "tag_effect` WHERE tag_id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int[] writeOwned(List<TitleData.OwnedTitle> owned, Map<Long, String> idMap,
                            UuidResolver resolver) throws SQLException {
        int unlockRows = 0;
        int equipped = 0;
        int skipped = 0;
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                for (TitleData.OwnedTitle o : owned) {
                    String newId = idMap.get(o.titleId());
                    java.util.UUID uuid = resolver.get(o.playerName());
                    if (uuid == null && !o.playerUuid().isEmpty()) {
                        try {
                            uuid = java.util.UUID.fromString(o.playerUuid());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    if (newId == null || uuid == null) {
                        if (uuid == null) {
                            skipped++;
                        }
                        continue;
                    }
                    long now = System.currentTimeMillis();
                    Long expires = o.expirationMillis() > UuidResolver.PERPETUAL_THRESHOLD
                            ? null : o.expirationMillis();
                    if (!unlockExists(c, uuid, newId)) {
                        insertUnlock(c, uuid, newId, now, expires);
                        unlockRows++;
                    }
                    if (o.isUse()) {
                        upsertEquipped(c, uuid, newId, now);
                        equipped++;
                    }
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        }
        return new int[]{unlockRows, equipped, skipped};
    }

    public int writeQuotas(List<TitleData.CustomQuota> quotas, UuidResolver resolver) throws SQLException {
        int rows = 0;
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                for (TitleData.CustomQuota q : quotas) {
                    java.util.UUID uuid = resolver.get(q.playerName());
                    if (uuid == null) {
                        continue;
                    }
                    upsertQuota(c, uuid, Math.max(0, q.num()), now);
                    rows++;
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        }
        return rows;
    }

    public int writeCoins(List<TitleData.CoinRow> coins, UuidResolver resolver) throws SQLException {
        int rows = 0;
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                for (TitleData.CoinRow coin : coins) {
                    java.util.UUID uuid = resolver.get(coin.playerName());
                    if (uuid == null && !coin.playerUuid().isEmpty()) {
                        try {
                            uuid = java.util.UUID.fromString(coin.playerUuid());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    if (uuid == null) {
                        continue;
                    }
                    upsertCoin(c, uuid, coin.amount(), now);
                    rows++;
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        }
        return rows;
    }

    private boolean unlockExists(Connection c, UUID uuid, String titleId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM `" + p + "unlock` WHERE player_uuid=? AND tag_id=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, titleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertUnlock(Connection c, UUID uuid, String titleId, long unlockedAt, Long expires) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO `" + p + "unlock`(player_uuid,tag_id,unlocked_at,expires_at) VALUES (?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, titleId);
            ps.setLong(3, unlockedAt);
            if (expires == null) {
                ps.setNull(4, Types.BIGINT);
            } else {
                ps.setLong(4, expires);
            }
            ps.executeUpdate();
        }
    }

    private void upsertEquipped(Connection c, UUID uuid, String titleId, long now) throws SQLException {
        try (PreparedStatement del = c.prepareStatement(
                "DELETE FROM `" + p + "player` WHERE player_uuid=?")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO `" + p + "player`(player_uuid,equipped_id,updated_at) VALUES (?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, titleId);
            ps.setLong(3, now);
            ps.executeUpdate();
        }
    }

    private void upsertQuota(Connection c, UUID uuid, int quota, long now) throws SQLException {
        try (PreparedStatement del = c.prepareStatement(
                "DELETE FROM `" + p + "quota` WHERE player_uuid=?")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO `" + p + "quota`(player_uuid,quota,updated_at) VALUES (?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, quota);
            ps.setLong(3, now);
            ps.executeUpdate();
        }
    }

    private void upsertCoin(Connection c, UUID uuid, long amount, long now) throws SQLException {
        try (PreparedStatement del = c.prepareStatement(
                "DELETE FROM `" + p + "coin` WHERE player_uuid=?")) {
            del.setString(1, uuid.toString());
            del.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO `" + p + "coin`(player_uuid,amount,updated_at) VALUES (?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, amount);
            ps.setLong(3, now);
            ps.executeUpdate();
        }
    }
}
