package com.dracave.tags.handlers;

import java.util.Locale;

public enum RewardKind {
    VAULT("vault"),
    PLAYER_POINTS("playerpoints"),
    COIN("coin");

    private final String id;

    RewardKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static RewardKind parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("reward kind is required");
        }
        return switch (value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "")) {
            case "vault" -> VAULT;
            case "playerpoints", "points", "pp" -> PLAYER_POINTS;
            case "coin", "tagcoin" -> COIN;
            default -> throw new IllegalArgumentException("unknown reward kind: " + value);
        };
    }
}
