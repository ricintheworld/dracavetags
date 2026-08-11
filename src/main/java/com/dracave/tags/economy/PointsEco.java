package com.dracave.tags.economy;

import com.dracave.tags.handlers.EcoType;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.UUID;

public final class PointsEco implements EcoProvider {
    private final Plugin playerPoints;

    public PointsEco() {
        Plugin pp = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        this.playerPoints = (pp != null && pp.isEnabled()) ? pp : null;
    }

    @Override
    public EcoType type() {
        return EcoType.PLAYER_POINTS;
    }

    @Override
    public boolean available() {
        return playerPoints != null;
    }

    @Override
    public BigDecimal balance(UUID playerId) {
        if (playerPoints == null) {
            return BigDecimal.ZERO;
        }
        try {
            Object api = playerPoints.getClass().getMethod("getAPI").invoke(playerPoints);
            int balance = (int) api.getClass().getMethod("look", UUID.class).invoke(api, playerId);
            return BigDecimal.valueOf(balance);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        if (playerPoints == null || amount.scale() > 0) {
            return false;
        }
        try {
            Object api = playerPoints.getClass().getMethod("getAPI").invoke(playerPoints);
            return (boolean) api.getClass().getMethod("take", UUID.class, int.class)
                    .invoke(api, playerId, amount.intValueExact());
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public boolean refund(UUID playerId, BigDecimal amount) {
        if (playerPoints == null || amount.scale() > 0) {
            return false;
        }
        try {
            Object api = playerPoints.getClass().getMethod("getAPI").invoke(playerPoints);
            return (boolean) api.getClass().getMethod("give", UUID.class, int.class)
                    .invoke(api, playerId, amount.intValueExact());
        } catch (Exception ex) {
            return false;
        }
    }
}
