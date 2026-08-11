package com.dracave.tags.handlers;

public record RewardCfg(long id, int number, RewardKind kind, long amount) {
    public RewardCfg {
        if (id <= 0) {
            throw new IllegalArgumentException("reward id must be positive");
        }
        if (number < 1) {
            throw new IllegalArgumentException("reward number must be >= 1");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("reward amount must be positive");
        }
    }
}
