package com.dracave.tags.storage;

import com.dracave.tags.handlers.ChatColorMode;
import com.dracave.tags.handlers.ChatColorPreference;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class ChatColorStore {
    private final DbPool pool;

    public ChatColorStore(DbPool pool) {
        this.pool = pool;
    }

    public ChatColorPreference load(UUID playerId) throws SQLException {
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT color_mode,custom_color FROM `" + pool.chatColorTbl() + "` WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new ChatColorPreference(ChatColorMode.parse(rows.getString(1)), rows.getString(2));
            }
        }
    }

    public void save(UUID playerId, ChatColorPreference preference) throws SQLException {
        String sql = pool.sqliteMode()
                ? "INSERT INTO `" + pool.chatColorTbl()
                + "` (player_uuid,color_mode,custom_color,updated_at) VALUES (?,?,?,?)"
                + " ON CONFLICT(player_uuid) DO UPDATE SET color_mode=excluded.color_mode,"
                + "custom_color=excluded.custom_color,updated_at=excluded.updated_at"
                : "INSERT INTO `" + pool.chatColorTbl()
                + "` (player_uuid,color_mode,custom_color,updated_at) VALUES (?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE color_mode=VALUES(color_mode),custom_color=VALUES(custom_color),"
                + "updated_at=VALUES(updated_at)";
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, preference.mode().name());
            statement.setString(3, preference.customColor());
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }
}