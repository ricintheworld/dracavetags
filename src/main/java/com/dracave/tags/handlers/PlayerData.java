package com.dracave.tags.handlers;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record PlayerData(UUID playerId, Set<String> unlocked, String equippedId, Map<String, Long> expirations) {
    public PlayerData {
        unlocked = Set.copyOf(unlocked);
        expirations = Map.copyOf(expirations);
    }

    public PlayerData(UUID playerId, Set<String> unlocked, String equippedId) {
        this(playerId, unlocked, equippedId, Map.of());
    }

    public PlayerData withUnlocked(Set<String> ids) {
        return new PlayerData(playerId, ids, equippedId, expirations);
    }

    public PlayerData withEquipped(String id) {
        return new PlayerData(playerId, unlocked, id, expirations);
    }

    public PlayerData withExpirations(Map<String, Long> expirations) {
        return new PlayerData(playerId, unlocked, equippedId, expirations);
    }

    public Long expiryOf(String tagId) {
        return expirations.get(tagId);
    }
}
