package com.dracave.tags.storage;

import com.dracave.tags.handlers.PlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerStore {
    private final DbPool pool;

    public PlayerStore(DbPool pool) {
        this.pool = pool;
    }

    public record EquippedSnap(UUID playerId, String equippedId, long updatedAt) {}

    public Map<UUID, EquippedSnap> batchLoadEquipped(Collection<UUID> playerIds) throws SQLException {
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EquippedSnap> result = new HashMap<>();
        List<UUID> ids = List.copyOf(playerIds);
        int batchSize = 500;
        for (int start = 0; start < ids.size(); start += batchSize) {
            int end = Math.min(start + batchSize, ids.size());
            List<UUID> batch = ids.subList(start, end);
            String placeholders = String.join(",", batch.stream().map(id -> "?").toList());
            String sql = "SELECT player_uuid,equipped_id,updated_at FROM `" + pool.playerTbl()
                    + "` WHERE player_uuid IN (" + placeholders + ")";
            try (Connection connection = pool.source().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < batch.size(); i++) {
                    statement.setString(i + 1, batch.get(i).toString());
                }
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.put(UUID.fromString(rows.getString(1)),
                                new EquippedSnap(UUID.fromString(rows.getString(1)),
                                        rows.getString(2), rows.getLong(3)));
                    }
                }
            }
        }
        return result;
    }

    public PlayerData load(UUID playerId) throws SQLException {
        String equippedId = null;
        try (Connection connection = pool.source().getConnection();
             PreparedStatement player = connection.prepareStatement(
                     "SELECT equipped_id FROM `" + pool.playerTbl() + "` WHERE player_uuid=?")) {
            player.setString(1, playerId.toString());
            try (ResultSet rows = player.executeQuery()) {
                if (rows.next()) {
                    equippedId = rows.getString(1);
                }
            }
        }
        Map<String, Long> unlocked = new HashMap<>();
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT tag_id, expires_at FROM `" + pool.unlockTbl() + "` WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long expiresAt = rows.getLong(2);
                    unlocked.put(rows.getString(1), rows.wasNull() ? null : expiresAt);
                }
            }
        }
        return new PlayerData(playerId, unlocked.keySet(), equippedId, unlocked);
    }

    public boolean unlock(UUID playerId, String tagId, int days) throws SQLException {
        Long expiresAt = days > 0 ? System.currentTimeMillis() + days * 86400000L : null;
        String sql = (pool.sqliteMode() ? "INSERT OR IGNORE" : "INSERT IGNORE")
                + " INTO `" + pool.unlockTbl()
                + "` (player_uuid,tag_id,unlocked_at,expires_at) VALUES (?,?,?,?)";
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, tagId);
            statement.setLong(3, System.currentTimeMillis());
            if (expiresAt == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, expiresAt);
            }
            return statement.executeUpdate() > 0;
        }
    }

    public boolean setExpiry(UUID playerId, String tagId, int days) throws SQLException {
        long now = System.currentTimeMillis();
        Long expiresAt = days > 0 ? now + days * 86400000L : null;
        String sql = pool.sqliteMode()
                ? "INSERT INTO `" + pool.unlockTbl()
                + "` (player_uuid,tag_id,unlocked_at,expires_at) VALUES (?,?,?,?)"
                + " ON CONFLICT(player_uuid,tag_id) DO UPDATE SET expires_at=excluded.expires_at"
                : "INSERT INTO `" + pool.unlockTbl()
                + "` (player_uuid,tag_id,unlocked_at,expires_at) VALUES (?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at)";
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, tagId);
            statement.setLong(3, now);
            if (expiresAt == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, expiresAt);
            }
            return statement.executeUpdate() > 0;
        }
    }

    public boolean extend(UUID playerId, String tagId, int days) throws SQLException {
        long now = System.currentTimeMillis();
        Long expiresAt = days > 0 ? now + days * 86400000L : null;
        String sql = pool.sqliteMode()
                ? "INSERT INTO `" + pool.unlockTbl()
                + "` (player_uuid,tag_id,unlocked_at,expires_at) VALUES (?,?,?,?)"
                + " ON CONFLICT(player_uuid,tag_id) DO UPDATE SET expires_at=CASE WHEN expires_at IS NULL THEN expires_at ELSE excluded.expires_at END"
                : "INSERT INTO `" + pool.unlockTbl()
                + "` (player_uuid,tag_id,unlocked_at,expires_at) VALUES (?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE expires_at=IF(expires_at IS NULL, expires_at, VALUES(expires_at))";
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, tagId);
            statement.setLong(3, now);
            if (expiresAt == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, expiresAt);
            }
            return statement.executeUpdate() > 0;
        }
    }

    public Map<String, Long> purgeExpired(UUID playerId) throws SQLException {
        Map<String, Long> removed = new HashMap<>();
        long now = System.currentTimeMillis();
        try (Connection connection = pool.source().getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT tag_id FROM `" + pool.unlockTbl()
                             + "` WHERE player_uuid=? AND expires_at IS NOT NULL AND expires_at<?")) {
            select.setString(1, playerId.toString());
            select.setLong(2, now);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    removed.put(rows.getString(1), now);
                }
            }
        }
        if (removed.isEmpty()) {
            return removed;
        }
        try (Connection connection = pool.source().getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM `" + pool.unlockTbl()
                             + "` WHERE player_uuid=? AND expires_at IS NOT NULL AND expires_at<?")) {
            delete.setString(1, playerId.toString());
            delete.setLong(2, now);
            delete.executeUpdate();
        }
        try (Connection connection = pool.source().getConnection();
             PreparedStatement clear = connection.prepareStatement(
                     "UPDATE `" + pool.playerTbl()
                             + "` SET equipped_id=NULL,updated_at=? WHERE player_uuid=? AND equipped_id IS NOT NULL"
                             + " AND equipped_id NOT IN (SELECT tag_id FROM `" + pool.unlockTbl()
                             + "` WHERE player_uuid=?)")) {
            clear.setLong(1, now);
            clear.setString(2, playerId.toString());
            clear.setString(3, playerId.toString());
            clear.executeUpdate();
        }
        return removed;
    }

    public boolean revoke(UUID playerId, String tagId) throws SQLException {
        try (Connection connection = pool.source().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement remove = connection.prepareStatement(
                        "DELETE FROM `" + pool.unlockTbl() + "` WHERE player_uuid=? AND tag_id=?")) {
                    remove.setString(1, playerId.toString());
                    remove.setString(2, tagId);
                    int changed = remove.executeUpdate();
                    if (changed == 0) {
                        connection.rollback();
                        return false;
                    }
                }
                try (PreparedStatement clear = connection.prepareStatement(
                        "UPDATE `" + pool.playerTbl()
                                + "` SET equipped_id=NULL,updated_at=? WHERE player_uuid=? AND equipped_id=?")) {
                    clear.setLong(1, System.currentTimeMillis());
                    clear.setString(2, playerId.toString());
                    clear.setString(3, tagId);
                    clear.executeUpdate();
                }
                try (PreparedStatement purchase = connection.prepareStatement(
                        "UPDATE `" + pool.purchaseTbl()
                                + "` SET state='" + com.dracave.tags.handlers.PurchasePhase.REVOKED.name()
                                + "',updated_at=? WHERE player_uuid=? AND tag_id=? AND state='"
                                + com.dracave.tags.handlers.PurchasePhase.COMPLETED.name() + "'")) {
                    purchase.setLong(1, System.currentTimeMillis());
                    purchase.setString(2, playerId.toString());
                    purchase.setString(3, tagId);
                    purchase.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    public int removeTagFromAll(String tagId) throws SQLException {
        try (Connection connection = pool.source().getConnection()) {
            connection.setAutoCommit(false);
            try {
                int removed;
                try (PreparedStatement remove = connection.prepareStatement(
                        "DELETE FROM `" + pool.unlockTbl() + "` WHERE tag_id=?")) {
                    remove.setString(1, tagId);
                    removed = remove.executeUpdate();
                }
                try (PreparedStatement clear = connection.prepareStatement(
                        "UPDATE `" + pool.playerTbl()
                                + "` SET equipped_id=NULL,updated_at=? WHERE equipped_id=?")) {
                    clear.setLong(1, System.currentTimeMillis());
                    clear.setString(2, tagId);
                    clear.executeUpdate();
                }
                connection.commit();
                return removed;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    public boolean equip(UUID playerId, String tagId) throws SQLException {
        String sql = pool.sqliteMode()
                ? "INSERT INTO `" + pool.playerTbl()
                + "` (player_uuid,equipped_id,updated_at) VALUES (?,?,?)"
                + " ON CONFLICT(player_uuid) DO UPDATE SET equipped_id=excluded.equipped_id,updated_at=excluded.updated_at"
                : "INSERT INTO `" + pool.playerTbl()
                + "` (player_uuid,equipped_id,updated_at) VALUES (?,?,?)"
                + " ON DUPLICATE KEY UPDATE equipped_id=VALUES(equipped_id),updated_at=VALUES(updated_at)";
        try (Connection connection = pool.source().getConnection()) {
            if (tagId != null) {
                try (PreparedStatement owned = connection.prepareStatement(
                        "SELECT 1 FROM `" + pool.unlockTbl() + "` WHERE player_uuid=? AND tag_id=?")) {
                    owned.setString(1, playerId.toString());
                    owned.setString(2, tagId);
                    try (ResultSet rows = owned.executeQuery()) {
                        if (!rows.next()) {
                            return false;
                        }
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, tagId);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return true;
        }
    }

    public boolean reservePurchase(UUID playerId, String tagId, UUID operationId, String currency, java.math.BigDecimal amount)
            throws SQLException {
        String sql = (pool.sqliteMode() ? "INSERT OR IGNORE" : "INSERT IGNORE")
                + " INTO `" + pool.purchaseTbl()
                + "` (operation_id,player_uuid,tag_id,currency,amount,state,created_at,updated_at) VALUES (?,?,?,?,?,'"
                + com.dracave.tags.handlers.PurchasePhase.PENDING.name() + "',?,?)";
        try (Connection connection = pool.source().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement owned = connection.prepareStatement(
                        "SELECT 1 FROM `" + pool.unlockTbl() + "` WHERE player_uuid=? AND tag_id=?")) {
                    owned.setString(1, playerId.toString());
                    owned.setString(2, tagId);
                    try (ResultSet result = owned.executeQuery()) {
                        if (result.next()) {
                            connection.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement cleanup = connection.prepareStatement(
                        "DELETE FROM `" + pool.purchaseTbl()
                                + "` WHERE player_uuid=? AND tag_id=? AND state IN ('"
                                + com.dracave.tags.handlers.PurchasePhase.FAILED.name() + "','"
                                + com.dracave.tags.handlers.PurchasePhase.REFUNDED.name() + "','"
                                + com.dracave.tags.handlers.PurchasePhase.REFUND_PENDING.name() + "','"
                                + com.dracave.tags.handlers.PurchasePhase.REVOKED.name() + "')")) {
                    cleanup.setString(1, playerId.toString());
                    cleanup.setString(2, tagId);
                    cleanup.executeUpdate();
                }
                long now = System.currentTimeMillis();
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, operationId.toString());
                    statement.setString(2, playerId.toString());
                    statement.setString(3, tagId);
                    statement.setString(4, currency);
                    statement.setBigDecimal(5, amount);
                    statement.setLong(6, now);
                    statement.setLong(7, now);
                    boolean inserted = statement.executeUpdate() > 0;
                    connection.commit();
                    return inserted;
                }
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    public boolean transitionPurchase(UUID operationId, com.dracave.tags.handlers.PurchasePhase fromState,
                                       com.dracave.tags.handlers.PurchasePhase toState, String reason) throws SQLException {
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE `" + pool.purchaseTbl()
                             + "` SET state=?,failure_reason=?,updated_at=? WHERE operation_id=? AND state=?")) {
            statement.setString(1, toState.name());
            statement.setString(2, reason);
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, operationId.toString());
            statement.setString(5, fromState.name());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean completePurchase(UUID playerId, String tagId, UUID operationId) throws SQLException {
        try (Connection connection = pool.source().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement unlock = connection.prepareStatement(
                        (pool.sqliteMode() ? "INSERT OR IGNORE" : "INSERT IGNORE")
                                + " INTO `" + pool.unlockTbl()
                                + "` (player_uuid,tag_id,unlocked_at,expires_at) VALUES (?,?,?,NULL)")) {
                    unlock.setString(1, playerId.toString());
                    unlock.setString(2, tagId);
                    unlock.setLong(3, System.currentTimeMillis());
                    unlock.executeUpdate();
                }
                try (PreparedStatement complete = connection.prepareStatement(
                        "UPDATE `" + pool.purchaseTbl()
                                + "` SET state='" + com.dracave.tags.handlers.PurchasePhase.COMPLETED.name()
                                + "',completed_at=?,updated_at=? WHERE operation_id=? AND state='"
                                + com.dracave.tags.handlers.PurchasePhase.CHARGED.name() + "'")) {
                    long now = System.currentTimeMillis();
                    complete.setLong(1, now);
                    complete.setLong(2, now);
                    complete.setString(3, operationId.toString());
                    if (complete.executeUpdate() == 0) {
                        connection.rollback();
                        return false;
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    public void forcePurchaseState(UUID operationId, com.dracave.tags.handlers.PurchasePhase state, String reason) throws SQLException {
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE `" + pool.purchaseTbl()
                             + "` SET state=?,failure_reason=?,updated_at=? WHERE operation_id=?")) {
            statement.setString(1, state.name());
            statement.setString(2, reason);
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, operationId.toString());
            statement.executeUpdate();
        }
    }

    public boolean markRefunded(UUID operationId, boolean refunded) throws SQLException {
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE `" + pool.purchaseTbl()
                             + "` SET refunded=?,updated_at=? WHERE operation_id=?")) {
            statement.setBoolean(1, refunded);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, operationId.toString());
            return statement.executeUpdate() > 0;
        }
    }

    public java.util.List<com.dracave.tags.handlers.PurchaseLog> findStalePurchases(long beforeTimestamp) throws SQLException {
        java.util.List<com.dracave.tags.handlers.PurchaseLog> result = new java.util.ArrayList<>();
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT operation_id,player_uuid,tag_id,currency,amount,state,failure_reason,refunded,"
                             + "created_at,updated_at,completed_at FROM `" + pool.purchaseTbl()
                             + "` WHERE state IN ('"
                             + com.dracave.tags.handlers.PurchasePhase.PENDING.name() + "','"
                             + com.dracave.tags.handlers.PurchasePhase.CHARGING.name() + "','"
                             + com.dracave.tags.handlers.PurchasePhase.CHARGED.name() + "') AND created_at<?")) {
            statement.setLong(1, beforeTimestamp);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Long completedAt = rows.getLong(11);
                    result.add(new com.dracave.tags.handlers.PurchaseLog(
                            UUID.fromString(rows.getString(1)),
                            UUID.fromString(rows.getString(2)),
                            rows.getString(3),
                            rows.getString(4),
                            rows.getBigDecimal(5),
                            rows.getString(6),
                            rows.getString(7),
                            rows.getBoolean(8),
                            rows.getLong(9),
                            rows.getLong(10),
                            rows.wasNull() ? null : completedAt));
                }
            }
        }
        return result;
    }

    public java.util.List<RankEntry> ranking(int limit) throws SQLException {
        java.util.List<RankEntry> result = new java.util.ArrayList<>();
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT u.player_uuid, COUNT(u.tag_id) AS unlocked, COALESCE(c.amount, 0) AS coin "
                             + "FROM `" + pool.unlockTbl() + "` u "
                             + "LEFT JOIN `" + pool.coinTbl() + "` c ON u.player_uuid=c.player_uuid "
                             + "GROUP BY u.player_uuid ORDER BY unlocked DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new RankEntry(
                            UUID.fromString(rows.getString(1)),
                            rows.getLong(2),
                            rows.getLong(3)));
                }
            }
        }
        return result;
    }
}
