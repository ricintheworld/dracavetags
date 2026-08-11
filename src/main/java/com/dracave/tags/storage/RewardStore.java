package com.dracave.tags.storage;

import com.dracave.tags.handlers.RewardCfg;
import com.dracave.tags.handlers.RewardKind;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RewardStore {
    private final DbPool pool;

    public RewardStore(DbPool pool) {
        this.pool = pool;
    }

    public List<RewardCfg> findAll() throws SQLException {
        List<RewardCfg> rewards = new ArrayList<>();
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id,number,reward_kind,amount FROM `" + pool.rewardTbl() + "` ORDER BY number")) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    rewards.add(new RewardCfg(rows.getLong(1), rows.getInt(2),
                            RewardKind.parse(rows.getString(3)), rows.getLong(4)));
                }
            }
        }
        return rewards;
    }

    public RewardCfg findById(long id) throws SQLException {
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id,number,reward_kind,amount FROM `" + pool.rewardTbl() + "` WHERE id=?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next()
                        ? new RewardCfg(rows.getLong(1), rows.getInt(2),
                                RewardKind.parse(rows.getString(3)), rows.getLong(4))
                        : null;
            }
        }
    }

    public RewardCfg add(RewardCfg reward) throws SQLException {
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO `" + pool.rewardTbl() + "` (id,number,reward_kind,amount) VALUES (?,?,?,?)")) {
            statement.setLong(1, reward.id());
            statement.setInt(2, reward.number());
            statement.setString(3, reward.kind().id());
            statement.setLong(4, reward.amount());
            statement.executeUpdate();
        }
        return reward;
    }

    public boolean isClaimed(UUID playerId, long rewardId) throws SQLException {
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM `" + pool.rewardLogTbl() + "` WHERE player_uuid=? AND reward_id=?")) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, rewardId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    public boolean claim(UUID playerId, long rewardId) throws SQLException {
        long id = UUID.randomUUID().getLeastSignificantBits() & Long.MAX_VALUE;
        try (Connection connection = pool.source().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO `" + pool.rewardLogTbl() + "` (id,player_uuid,reward_id,claimed_at) VALUES (?,?,?,?)")) {
            statement.setLong(1, id);
            statement.setString(2, playerId.toString());
            statement.setLong(3, rewardId);
            statement.setLong(4, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        }
    }
}
