package com.dracave.tags.economy;

import com.dracave.tags.handlers.EcoType;
import com.dracave.tags.storage.CoinStore;

import java.math.BigDecimal;
import java.util.UUID;

public final class CoinEco implements EcoProvider {
    private final CoinStore store;

    public CoinEco(CoinStore store) {
        this.store = store;
    }

    @Override
    public EcoType type() {
        return EcoType.COIN;
    }

    @Override
    public boolean available() {
        return store != null;
    }

    @Override
    public BigDecimal balance(UUID playerId) {
        if (store == null) {
            return BigDecimal.ZERO;
        }
        try {
            return BigDecimal.valueOf(store.balance(playerId));
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        if (store == null || amount.scale() > 0 || amount.signum() <= 0) {
            return false;
        }
        try {
            return store.subtract(playerId, amount.longValueExact());
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public boolean refund(UUID playerId, BigDecimal amount) {
        if (store == null || amount.scale() > 0 || amount.signum() <= 0) {
            return false;
        }
        try {
            return store.add(playerId, amount.longValueExact());
        } catch (Exception ex) {
            return false;
        }
    }
}
