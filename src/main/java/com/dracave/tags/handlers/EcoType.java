package com.dracave.tags.handlers;

import java.util.Locale;

public enum EcoType {
    VAULT("vault"),
    PLAYER_POINTS("playerpoints"),
    COIN("coin"),
    ITEM("item");

    private final String id;

    EcoType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static EcoType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("shop.currency is required");
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return switch (normalized) {
            case "vault" -> VAULT;
            case "playerpoints", "points", "pp" -> PLAYER_POINTS;
            case "coin", "tagcoin" -> COIN;
            case "item", "itemstack", "物品" -> ITEM;
            default -> throw new IllegalArgumentException("unknown shop currency: " + value);
        };
    }
}
