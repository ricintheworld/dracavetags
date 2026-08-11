package com.dracave.tags.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public final class CoinStore {
    private final DbPool pool;

    public CoinStore(DbPool pool) {
        this.pool = pool;
    }

    public long balance(UUID playerId) throws SQLException {
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT amount FROM `" + pool.coinTbl() + "` WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        }
    }

    public boolean add(UUID playerId, long amount) throws SQLException {
        if (amount <= 0) {
            return false;
        }
        String sql = pool.sqliteMode()
                ? "INSERT INTO `" + pool.coinTbl() + "` (player_uuid,amount,updated_at) VALUES (?,?,?)"
                + " ON CONFLICT(player_uuid) DO UPDATE SET amount=amount+excluded.amount,updated_at=excluded.updated_at"
                : "INSERT INTO `" + pool.coinTbl() + "` (player_uuid,amount,updated_at) VALUES (?,?,?)"
                + " ON DUPLICATE KEY UPDATE amount=amount+VALUES(amount),updated_at=VALUES(updated_at)";
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, amount);
            statement.setLong(3, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean subtract(UUID playerId, long amount) throws SQLException {
        if (amount <= 0) {
            return false;
        }
        String sql = "UPDATE `" + pool.coinTbl()
                + "` SET amount=amount-?,updated_at=? WHERE player_uuid=? AND amount>=?";
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, amount);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, playerId.toString());
            statement.setLong(4, amount);
            return statement.executeUpdate() > 0;
        }
    }
}
