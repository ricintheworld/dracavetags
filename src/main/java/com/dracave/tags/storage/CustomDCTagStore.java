package com.dracave.tags.storage;

import com.dracave.tags.handlers.CustomDCTag;
import com.dracave.tags.handlers.DCTagType;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public final class CustomDCTagStore {
    private final DbPool pool;

    public CustomDCTagStore(DbPool pool) {
        this.pool = pool;
    }

    public List<CustomDCTag> loadActive() throws SQLException {
        List<CustomDCTag> result = new ArrayList<>();
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT tag_id,owner_uuid,tag_text,type,colors,frames,period_ticks,icon,revision,"
                             + "created_at,updated_at FROM `" + pool.customTbl() + "` WHERE status='ACTIVE'")) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(read(rows));
                }
            }
        }
        return result;
    }

    public boolean create(CustomDCTag tag) throws SQLException {
        try (Connection connection = pool.source().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO `" + pool.customTbl()
                                + "` (tag_id,owner_uuid,tag_text,type,colors,frames,period_ticks,icon,status,"
                                + "revision,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,'ACTIVE',?,?,?)")) {
                    write(insert, tag);
                    insert.executeUpdate();
                }
                String sql = (pool.sqliteMode() ? "INSERT OR IGNORE" : "INSERT IGNORE")
                        + " INTO `" + pool.unlockTbl()
                        + "` (player_uuid,tag_id,unlocked_at,expires_at) VALUES (?,?,?,NULL)";
                try (PreparedStatement unlock = connection.prepareStatement(sql)) {
                    unlock.setString(1, tag.ownerId().toString());
                    unlock.setString(2, tag.id());
                    unlock.setLong(3, tag.createdAt());
                    unlock.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    public boolean update(CustomDCTag tag, int expectedRevision) throws SQLException {
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE `" + pool.customTbl()
                             + "` SET tag_text=?,type=?,colors=?,frames=?,period_ticks=?,icon=?,"
                             + "revision=revision+1,updated_at=? WHERE tag_id=? AND owner_uuid=? AND revision=?"
                             + " AND status='ACTIVE'")) {
            statement.setString(1, tag.text());
            statement.setString(2, tag.type().name());
            statement.setString(3, encode(tag.colors()));
            statement.setString(4, encode(tag.frames()));
            statement.setInt(5, tag.periodTicks());
            statement.setString(6, tag.icon());
            statement.setLong(7, tag.updatedAt());
            statement.setString(8, tag.id());
            statement.setString(9, tag.ownerId().toString());
            statement.setInt(10, expectedRevision);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean delete(UUID ownerId, String tagId) throws SQLException {
        try (Connection connection = pool.source().getConnection()) {
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                try (PreparedStatement mark = connection.prepareStatement(
                        "UPDATE `" + pool.customTbl()
                                + "` SET status='DELETED',deleted_at=?,updated_at=? WHERE tag_id=? AND owner_uuid=? AND status='ACTIVE'")) {
                    mark.setLong(1, now);
                    mark.setLong(2, now);
                    mark.setString(3, tagId);
                    mark.setString(4, ownerId.toString());
                    if (mark.executeUpdate() == 0) {
                        connection.rollback();
                        return false;
                    }
                }
                try (PreparedStatement unlock = connection.prepareStatement(
                        "DELETE FROM `" + pool.unlockTbl() + "` WHERE tag_id=?")) {
                    unlock.setString(1, tagId);
                    unlock.executeUpdate();
                }
                try (PreparedStatement clear = connection.prepareStatement(
                        "UPDATE `" + pool.playerTbl() + "` SET equipped_id=NULL,updated_at=? WHERE equipped_id=?")) {
                    clear.setLong(1, now);
                    clear.setString(2, tagId);
                    clear.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private static CustomDCTag read(ResultSet row) throws SQLException {
        return new CustomDCTag(
                row.getString(1),
                UUID.fromString(row.getString(2)),
                row.getString(3),
                DCTagType.valueOf(row.getString(4)),
                decode(row.getString(5)),
                decode(row.getString(6)),
                row.getInt(7),
                row.getString(8),
                row.getInt(9),
                row.getLong(10),
                row.getLong(11)
        );
    }

    private static void write(PreparedStatement statement, CustomDCTag tag) throws SQLException {
        statement.setString(1, tag.id());
        statement.setString(2, tag.ownerId().toString());
        statement.setString(3, tag.text());
        statement.setString(4, tag.type().name());
        statement.setString(5, encode(tag.colors()));
        statement.setString(6, encode(tag.frames()));
        statement.setInt(7, tag.periodTicks());
        statement.setString(8, tag.icon());
        statement.setInt(9, tag.revision());
        statement.setLong(10, tag.createdAt());
        statement.setLong(11, tag.updatedAt());
    }

    private static String encode(List<String> values) {
        return values.stream()
                .map(value -> Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)))
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private static List<String> decode(String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(part -> new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8))
                .toList();
    }
}
