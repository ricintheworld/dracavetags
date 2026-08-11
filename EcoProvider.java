package com.dracave.tags.economy;

import com.dracave.tags.handlers.EcoType;

import java.math.BigDecimal;
import java.util.UUID;

public interface EcoProvider {
    EcoType type();
    boolean available();
    BigDecimal balance(UUID playerId);
    boolean withdraw(UUID playerId, BigDecimal amount);
    boolean refund(UUID playerId, BigDecimal amount);
}
