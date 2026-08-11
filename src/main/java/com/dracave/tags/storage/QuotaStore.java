package com.dracave.tags.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public final class QuotaStore {
    private final DbPool pool;

    public QuotaStore(DbPool pool) {
        this.pool = pool;
    }

    public int quota(UUID playerId) throws SQLException {
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT quota FROM `" + pool.quotaTbl() + "` WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }

    public boolean setQuota(UUID playerId, int quota) throws SQLException {
        if (quota < 0) {
            return false;
        }
        String sql = pool.sqliteMode()
                ? "INSERT INTO `" + pool.quotaTbl() + "` (player_uuid,quota,updated_at) VALUES (?,?,?)"
                + " ON CONFLICT(player_uuid) DO UPDATE SET quota=excluded.quota,updated_at=excluded.updated_at"
                : "INSERT INTO `" + pool.quotaTbl() + "` (player_uuid,quota,updated_at) VALUES (?,?,?)"
                + " ON DUPLICATE KEY UPDATE quota=VALUES(quota),updated_at=VALUES(updated_at)";
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, quota);
            statement.setLong(3, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean addQuota(UUID playerId, int quota) throws SQLException {
        if (quota <= 0) {
            return false;
        }
        String sql = pool.sqliteMode()
                ? "INSERT INTO `" + pool.quotaTbl() + "` (player_uuid,quota,updated_at) VALUES (?,?,?)"
                + " ON CONFLICT(player_uuid) DO UPDATE SET quota=quota+excluded.quota,updated_at=excluded.updated_at"
                : "INSERT INTO `" + pool.quotaTbl() + "` (player_uuid,quota,updated_at) VALUES (?,?,?)"
                + " ON DUPLICATE KEY UPDATE quota=quota+VALUES(quota),updated_at=VALUES(updated_at)";
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, quota);
            statement.setLong(3, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        }
    }
}
