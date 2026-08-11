package com.dracave.tags.handlers;

public record DCTagPotion(String effectType, int level) {
    public DCTagPotion {
        if (effectType == null || effectType.isBlank()) {
            throw new IllegalArgumentException("effect type is required");
        }
        if (level < 1) {
            throw new IllegalArgumentException("effect level must be >= 1");
        }
    }
}
