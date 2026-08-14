package com.dracave.tags.migrate.core;

public final class TitleData {
    public record SourceTitle(long id, String titleName, String buyType, long amount, int day,
                              String itemStack, boolean isHide, String description, String icon) {
    }

    public record OwnedTitle(long id, String playerName, String playerUuid, long titleId, String titleName,
                             long expirationMillis, boolean isUse) {
    }

    public record TitleBuff(long id, long titleId, String buffType, String potionName, int potionLevel) {
    }

    public record CustomQuota(String playerName, int num, int useNum) {
    }

    public record CoinRow(String playerName, String playerUuid, long amount) {
    }

    public record MigrationReport(
            int definitions, int owners, int players, int equipped,
            int buffs, int quotas, int coins,
            java.util.List<String> warnings) {
        public int totalPlayers() {
            return players;
        }

        public String summary() {
            return "定义 " + definitions + " 条，拥有记录 " + owners + " 条 / " + players + " 名玩家，装备 " + equipped + " 人，"
                    + "药水 " + buffs + " 条，配额 " + quotas + " 条，称号币 " + coins + " 条";
        }
    }
}
