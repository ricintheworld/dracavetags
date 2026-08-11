package com.dracave.tags.storage;

import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.DCTagAnim;
import com.dracave.tags.handlers.DCTagOffer;
import com.dracave.tags.handlers.DCTagPart;
import com.dracave.tags.handlers.DCTagPotion;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DCTagStore {
    private final DbPool pool;

    public DCTagStore(DbPool pool) {
        this.pool = pool;
    }

    public List<DCTag> loadAll() throws SQLException {
        Map<String, DCTag> byId = new HashMap<>();
        Map<String, List<String>> descriptions = new HashMap<>();
        Map<String, List<String>> colors = new HashMap<>();
        Map<String, List<String>> frames = new HashMap<>();
        Map<String, List<DCTagPotion>> effects = new HashMap<>();
        Map<String, String> animationTypes = new HashMap<>();
        Map<String, Integer> periodTicksMap = new HashMap<>();

        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT tag_id,tag_text,icon,sort_order,default_unlocked,permission_node,shop_hidden,"
                             + "shop_currency,shop_price,gradient_period_ticks,animation_type,"
                             + "particle_type,particle_id,particle_colors,revision FROM `" + pool.defTbl() + "`")) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String id = rows.getString(1);
                    String currencyName = rows.getString(8);
                    DCTagOffer offer = null;
                    if (currencyName != null) {
                        try {
                            offer = DCTagOffer.parseStored(currencyName, rows.getBigDecimal(9));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    int periodTicks = rows.getInt(10);
                    if (!rows.wasNull() && periodTicks > 0) {
                        periodTicksMap.put(id, periodTicks);
                    }
                    String animationType = rows.getString(11);
                    if (animationType != null && !animationType.isBlank()) {
                        animationTypes.put(id, animationType);
                    }
                    String particleType = rows.getString(12);
                    DCTagPart particle = null;
                    if (particleType != null) {
                        String particleId = rows.getString(13);
                        String particleColors = rows.getString(14);
                        List<String> particleColorList = particleColors == null || particleColors.isBlank()
                                ? List.of() : List.of(particleColors.split(","));
                        particle = new DCTagPart(particleType, particleId, particleColorList);
                    }
                    byId.put(id, new DCTag(
                            id, rows.getString(2), List.of(), rows.getString(3), rows.getInt(4),
                            rows.getBoolean(5), rows.getString(6), null, offer,
                            List.of(), rows.getBoolean(7), List.of(), particle, rows.getInt(15)));
                }
            }
        }

        loadStrings(descriptions, pool.descTbl(), "description_text");
        loadStrings(colors, pool.colorTbl(), "color");
        loadStrings(frames, pool.frameTbl(), "frame_text");

        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT tag_id,effect_type,effect_level FROM `" + pool.effectTbl() + "` ORDER BY position")) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    effects.computeIfAbsent(rows.getString(1), ignored -> new ArrayList<>())
                            .add(new DCTagPotion(rows.getString(2), rows.getInt(3)));
                }
            }
        }

        List<DCTag> result = new ArrayList<>();
        for (DCTag definition : byId.values()) {
            List<String> tagColors = colors.getOrDefault(definition.id(), List.of());
            DCTagAnim animation = rebuildAnimation(tagColors,
                    animationTypes.get(definition.id()),
                    periodTicksMap.getOrDefault(definition.id(), 0),
                    frames.getOrDefault(definition.id(), List.of()));
            result.add(new DCTag(
                    definition.id(), definition.display(),
                    descriptions.getOrDefault(definition.id(), List.of()),
                    definition.icon(), definition.order(), definition.defaultUnlocked(), definition.permission(),
                    animation, definition.purchaseOffer(),
                    tagColors, definition.shopHidden(),
                    effects.getOrDefault(definition.id(), List.of()), definition.particle(), definition.revision()));
        }
        result.sort(Comparator.comparingInt(DCTag::order).thenComparing(DCTag::id));
        return result;
    }

    private DCTagAnim rebuildAnimation(List<String> colors, String animationType, int periodTicks, List<String> frames) {
        if (periodTicks <= 0) {
            return null;
        }
        DCTagAnim.GradientStyle style = DCTagAnim.GradientStyle.CYCLE;
        String baseType = animationType == null ? "" : animationType;
        if (baseType.endsWith(":PINGPONG")) {
            style = DCTagAnim.GradientStyle.PINGPONG;
            baseType = baseType.substring(0, baseType.length() - ":PINGPONG".length());
        }
        return switch (baseType) {
            case "RAINBOW" -> DCTagAnim.rainbow(periodTicks);
            case "SOLID_GRADIENT" -> colors.size() >= 2
                    ? new DCTagAnim(DCTagAnim.AnimType.SOLID_GRADIENT, colors, List.of(), periodTicks, style) : null;
            case "FLASHING_COLORS" -> colors.size() >= 2
                    ? new DCTagAnim(DCTagAnim.AnimType.FLASHING_COLORS, colors, List.of(), periodTicks) : null;
            case "TEXT_FRAMES" -> frames.size() >= 2
                    ? new DCTagAnim(DCTagAnim.AnimType.TEXT_FRAMES, List.of(), frames, periodTicks) : null;
            default -> colors.size() >= 2 ? new DCTagAnim(colors, periodTicks, style) : null;
        };
    }

    private void loadStrings(Map<String, List<String>> target, String table, String column) throws SQLException {
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT tag_id," + column + " FROM `" + table + "` ORDER BY position")) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    target.computeIfAbsent(rows.getString(1), ignored -> new ArrayList<>()).add(rows.getString(2));
                }
            }
        }
    }

    public UpsertOutcome upsertAll(List<DCTag> definitions) throws SQLException {
        int inserted = 0;
        int updated = 0;
        try (Connection connection = pool.source().getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (DCTag definition : definitions) {
                    boolean exists;
                    try (PreparedStatement check = connection.prepareStatement(
                            "SELECT 1 FROM `" + pool.defTbl() + "` WHERE tag_id=?")) {
                        check.setString(1, definition.id());
                        try (ResultSet rows = check.executeQuery()) {
                            exists = rows.next();
                        }
                    }
                    if (exists) {
                        update(connection, definition);
                        updated++;
                    } else {
                        insert(connection, definition);
                        inserted++;
                    }
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
        return new UpsertOutcome(inserted, updated, definitions);
    }

    public record UpsertOutcome(int inserted, int updated, List<DCTag> definitions) {}

    private void insert(Connection connection, DCTag definition) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO `" + pool.defTbl()
                        + "` (tag_id,tag_text,icon,sort_order,default_unlocked,permission_node,shop_hidden,"
                        + "shop_currency,shop_price,gradient_period_ticks,animation_type,"
                        + "particle_type,particle_id,particle_colors,revision,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, definition.id());
            bindMutable(statement, definition, 2);
            statement.setInt(15, 0);
            statement.setLong(16, now);
            statement.setLong(17, now);
            statement.executeUpdate();
        }
        replaceChildren(connection, definition);
    }

    private void update(Connection connection, DCTag definition) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE `" + pool.defTbl()
                        + "` SET tag_text=?,icon=?,sort_order=?,default_unlocked=?,permission_node=?,shop_hidden=?,"
                        + "shop_currency=?,shop_price=?,gradient_period_ticks=?,animation_type=?,"
                        + "particle_type=?,particle_id=?,particle_colors=?,revision=revision+1,updated_at=?"
                        + " WHERE tag_id=?")) {
            bindMutable(statement, definition, 1);
            statement.setLong(14, now);
            statement.setString(15, definition.id());
            statement.executeUpdate();
        }
        replaceChildren(connection, definition);
    }

    private void bindMutable(PreparedStatement statement, DCTag definition, int offset) throws SQLException {
        statement.setString(offset, definition.display());
        statement.setString(offset + 1, definition.icon());
        statement.setInt(offset + 2, definition.order());
        statement.setBoolean(offset + 3, definition.defaultUnlocked());
        statement.setString(offset + 4, definition.permission());
        statement.setBoolean(offset + 5, definition.shopHidden());
        if (definition.purchaseOffer() == null) {
            statement.setNull(offset + 6, java.sql.Types.VARCHAR);
            statement.setNull(offset + 7, java.sql.Types.DECIMAL);
        } else {
            statement.setString(offset + 6, definition.purchaseOffer().storedCurrency());
            statement.setBigDecimal(offset + 7, definition.purchaseOffer().price());
        }
        DCTagAnim animation = definition.animation();
        if (animation == null) {
            statement.setNull(offset + 8, java.sql.Types.INTEGER);
            statement.setNull(offset + 9, java.sql.Types.VARCHAR);
        } else {
            statement.setInt(offset + 8, animation.periodTicks());
            String animType = animation.type().name();
            if (animation.style() == DCTagAnim.GradientStyle.PINGPONG) {
                animType += ":PINGPONG";
            }
            statement.setString(offset + 9, animType);
        }
        if (definition.particle() == null) {
            statement.setNull(offset + 10, java.sql.Types.VARCHAR);
            statement.setNull(offset + 11, java.sql.Types.VARCHAR);
            statement.setNull(offset + 12, java.sql.Types.VARCHAR);
        } else {
            statement.setString(offset + 10, definition.particle().particleType());
            statement.setString(offset + 11, definition.particle().particleId());
            statement.setString(offset + 12, String.join(",", definition.particle().colors()));
        }
    }

    private void replaceChildren(Connection connection, DCTag definition) throws SQLException {
        deleteChildren(connection, pool.descTbl(), definition.id());
        deleteChildren(connection, pool.colorTbl(), definition.id());
        deleteChildren(connection, pool.effectTbl(), definition.id());
        deleteChildren(connection, pool.frameTbl(), definition.id());
        insertStrings(connection, pool.descTbl(), "description_text", definition.id(), definition.description());
        insertStrings(connection, pool.colorTbl(), "color", definition.id(), definition.colors());
        DCTagAnim animation = definition.animation();
        if (animation != null && animation.type() == DCTagAnim.AnimType.TEXT_FRAMES) {
            insertStrings(connection, pool.frameTbl(), "frame_text", definition.id(), animation.frames());
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO `" + pool.effectTbl()
                        + "` (tag_id,position,effect_type,effect_level) VALUES (?,?,?,?)")) {
            for (int i = 0; i < definition.potionEffects().size(); i++) {
                DCTagPotion effect = definition.potionEffects().get(i);
                statement.setString(1, definition.id());
                statement.setInt(2, i);
                statement.setString(3, effect.effectType());
                statement.setInt(4, effect.level());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void deleteChildren(Connection connection, String table, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM `" + table + "` WHERE tag_id=?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }

    private void insertStrings(Connection connection, String table, String column, String id, List<String> values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO `" + table + "` (tag_id,position," + column + ") VALUES (?,?,?)")) {
            for (int i = 0; i < values.size(); i++) {
                statement.setString(1, id);
                statement.setInt(2, i);
                statement.setString(3, values.get(i));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public boolean update(DCTag definition, int expectedRevision) throws SQLException {
        try (Connection connection = pool.source().getConnection()) {
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE `" + pool.defTbl()
                                + "` SET tag_text=?,icon=?,sort_order=?,default_unlocked=?,permission_node=?,"
                                + "shop_hidden=?,shop_currency=?,shop_price=?,gradient_period_ticks=?,animation_type=?,"
                                + "particle_type=?,particle_id=?,particle_colors=?,revision=revision+1,updated_at=?"
                                + " WHERE tag_id=? AND revision=?")) {
                    bindMutable(statement, definition, 1);
                    statement.setLong(14, now);
                    statement.setString(15, definition.id());
                    statement.setInt(16, expectedRevision);
                    if (statement.executeUpdate() == 0) {
                        connection.rollback();
                        return false;
                    }
                }
                replaceChildren(connection, definition);
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    public boolean delete(String tagId) throws SQLException {
        try (Connection connection = pool.source().getConnection()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM `" + pool.defTbl() + "` WHERE tag_id=?")) {
                    statement.setString(1, tagId);
                    changed = statement.executeUpdate();
                }
                deleteChildren(connection, pool.descTbl(), tagId);
                deleteChildren(connection, pool.colorTbl(), tagId);
                deleteChildren(connection, pool.effectTbl(), tagId);
                deleteChildren(connection, pool.frameTbl(), tagId);
                connection.commit();
                return changed > 0;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
}
