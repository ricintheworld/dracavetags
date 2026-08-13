package com.dracave.tags.economy;

import com.dracave.tags.handlers.EcoType;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * PlayerPoints 经济桥接器。
 * 参考 PluginBase: 用 isPresent 守卫避免类加载阶段异常。
 */
public final class PointsEco implements EcoProvider {
    private volatile Plugin playerPoints;

    public PointsEco() {
        detect();
    }

    @Override
    public void refresh() {
        detect();
    }

    private void detect() {
        try {
            if (!isPresent("org.black_ixx.playerpoints.PlayerPointsAPI")) return;
            Plugin pp = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            this.playerPoints = (pp != null && pp.isEnabled()) ? pp : null;
        } catch (Throwable ignored) {
        }
    }

    @Override
    public EcoType type() { return EcoType.PLAYER_POINTS; }
    @Override
    public boolean available() { return playerPoints != null; }

    @Override
    public BigDecimal balance(UUID playerId) {
        Plugin pp = playerPoints;
        if (pp == null) return BigDecimal.ZERO;
        try {
            Object api = pp.getClass().getMethod("getAPI").invoke(pp);
            int balance = (int) api.getClass().getMethod("look", UUID.class).invoke(api, playerId);
            return BigDecimal.valueOf(balance);
        } catch (Exception ex) { return BigDecimal.ZERO; }
    }

    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        Plugin pp = playerPoints;
        if (pp == null || amount.scale() > 0) return false;
        try {
            Object api = pp.getClass().getMethod("getAPI").invoke(pp);
            int bal = (int) api.getClass().getMethod("look", UUID.class).invoke(api, playerId);
            if (bal < amount.intValueExact()) return false;
            return (boolean) api.getClass().getMethod("take", UUID.class, int.class)
                    .invoke(api, playerId, amount.intValueExact());
        } catch (Exception ex) { return false; }
    }

    @Override
    public boolean refund(UUID playerId, BigDecimal amount) {
        Plugin pp = playerPoints;
        if (pp == null || amount.scale() > 0) return false;
        try {
            Object api = pp.getClass().getMethod("getAPI").invoke(pp);
            return (boolean) api.getClass().getMethod("give", UUID.class, int.class)
                    .invoke(api, playerId, amount.intValueExact());
        } catch (Exception ex) { return false; }
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}