package com.dracave.tags.storage;

import java.util.UUID;

/**
 * 排行榜条目：playerId 是查询的 {@code player_uuid}，unlockedCount 是已解锁的称号数，coinBalance 是该玩家的代币余额。
 */
public record RankEntry(UUID playerId, long unlockedCount, long coinBalance) {
}
