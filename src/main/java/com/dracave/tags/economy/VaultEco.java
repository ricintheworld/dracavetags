package com.dracave.tags.economy;

import com.dracave.tags.handlers.EcoType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vault 经济桥接器（纯反射实现）。
 *
 * Paper 的 FoliaLifecycleChecker 会拦截插件类在加载阶段引用 net.milkbowl.vault.economy.Economy，
 * 抛出 "Cannot get plugin for class ... from a static initializer"。
 * 因此本类不直接 import Vault API，全部通过反射调用。
 */
public final class VaultEco implements EcoProvider {
    private volatile Object economy;
    private volatile Class<?> economyClass;

    public VaultEco() {
        detect();
    }

    @Override
    public void refresh() {
        detect();
    }

    private void detect() {
        try {
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
            org.bukkit.plugin.ServicesManager sm = Bukkit.getServicesManager();
            org.bukkit.plugin.RegisteredServiceProvider<?> rsp = sm.getRegistration(ecoClass);
            if (rsp != null) {
                economy = rsp.getProvider();
                economyClass = ecoClass;
            }
        } catch (Throwable ignored) {
        }
    }

    @Override public EcoType type() { return EcoType.VAULT; }
    @Override public boolean available() { return economy != null; }

    private static String resolveName(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) return online.getName();
        OfflinePlayer off = Bukkit.getOfflinePlayer(id);
        String name = off.getName();
        return name != null ? name : off.getUniqueId().toString();
    }

    @Override
    public BigDecimal balance(UUID playerId) {
        Object eco = economy;
        if (eco == null) return BigDecimal.ZERO;
        try {
            Method m = economyClass.getMethod("getBalance", String.class);
            Number bal = (Number) m.invoke(eco, resolveName(playerId));
            return BigDecimal.valueOf(bal.doubleValue());
        } catch (Exception ex) { return BigDecimal.ZERO; }
    }

    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        Object eco = economy;
        if (eco == null) return false;
        try {
            OfflinePlayer off = Bukkit.getOfflinePlayer(playerId);
            // 先检查余额
            Method hasMethod = economyClass.getMethod("has", OfflinePlayer.class, double.class);
            boolean has = (boolean) hasMethod.invoke(eco, off, amount.doubleValue());
            if (!has) return false;
            // 扣款
            Method withdrawMethod = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            Object resp = withdrawMethod.invoke(eco, off, amount.doubleValue());
            Class<?> respClass = resp.getClass();
            boolean ok = (boolean) respClass.getMethod("transactionSuccess").invoke(resp);
            if (!ok) {
                String msg = (String) respClass.getMethod("errorMessage").invoke(resp);
                Bukkit.getLogger().warning("[DraCaveTags] Vault withdraw 失败: " + msg);
            }
            return ok;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[DraCaveTags] Vault withdraw 异常: " + ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean refund(UUID playerId, BigDecimal amount) {
        Object eco = economy;
        if (eco == null) return false;
        try {
            OfflinePlayer off = Bukkit.getOfflinePlayer(playerId);
            Method depositMethod = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            Object resp = depositMethod.invoke(eco, off, amount.doubleValue());
            return (boolean) resp.getClass().getMethod("transactionSuccess").invoke(resp);
        } catch (Exception ex) { return false; }
    }
}