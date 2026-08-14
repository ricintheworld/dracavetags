package com.dracave.tags.economy;

import com.dracave.tags.handlers.EcoType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.Field;
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
    private volatile Method getBalanceMethod;
    private volatile Method hasMethod;
    private volatile Method withdrawPlayerMethod;
    private volatile Method depositPlayerMethod;
    private volatile Method transactionSuccessMethod;
    private volatile Field errorMessageField;

    public VaultEco() {
        if (!detect()) {
            // 延迟重试：Vault 可能尚未加载完成
            Bukkit.getScheduler().runTaskLater(
                    Bukkit.getPluginManager().getPlugin("DraCaveTags"), this::detect, 20L);
        }
    }

    @Override
    public void refresh() {
        detect();
    }

    private boolean detect() {
        try {
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
            org.bukkit.plugin.ServicesManager sm = Bukkit.getServicesManager();
            org.bukkit.plugin.RegisteredServiceProvider<?> rsp = sm.getRegistration(ecoClass);
            if (rsp != null) {
                Object provider = rsp.getProvider();
                // 预缓存所有方法引用
                getBalanceMethod = ecoClass.getMethod("getBalance", OfflinePlayer.class);
                hasMethod = ecoClass.getMethod("has", OfflinePlayer.class, double.class);
                withdrawPlayerMethod = ecoClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
                depositPlayerMethod = ecoClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
                // EconomyResponse 的方法和字段
                Class<?> respClass = withdrawPlayerMethod.getReturnType();
                transactionSuccessMethod = respClass.getMethod("transactionSuccess");
                errorMessageField = respClass.getField("errorMessage");  // errorMessage 是 public final 字段，不是方法

                economy = provider;
                economyClass = ecoClass;
                return true;
            }
        } catch (Throwable ex) {
            Bukkit.getLogger().warning("[DraCaveTags] Vault 经济检测失败: " + ex.getMessage());
        }
        return false;
    }

    @Override
    public EcoType type() {
        return EcoType.VAULT;
    }

    @Override
    public boolean available() {
        return economy != null;
    }

    @Override
    public BigDecimal balance(UUID playerId) {
        Object eco = economy;
        if (eco == null) return BigDecimal.ZERO;
        try {
            OfflinePlayer off = Bukkit.getOfflinePlayer(playerId);
            Number bal = (Number) getBalanceMethod.invoke(eco, off);
            return BigDecimal.valueOf(bal.doubleValue());
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[DraCaveTags] Vault balance 查询异常 player=" + playerId + ": " + ex.getMessage());
            return BigDecimal.ZERO;
        }
    }

    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        Object eco = economy;
        if (eco == null) return false;
        try {
            OfflinePlayer off = Bukkit.getOfflinePlayer(playerId);
            boolean has = (boolean) hasMethod.invoke(eco, off, amount.doubleValue());
            if (!has) return false;
            Object resp = withdrawPlayerMethod.invoke(eco, off, amount.doubleValue());
            boolean ok = (boolean) transactionSuccessMethod.invoke(resp);
            if (!ok) {
                String msg = (String) errorMessageField.get(resp);
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
            Object resp = depositPlayerMethod.invoke(eco, off, amount.doubleValue());
            boolean ok = (boolean) transactionSuccessMethod.invoke(resp);
            if (!ok) {
                String msg = (String) errorMessageField.get(resp);
                Bukkit.getLogger().warning("[DraCaveTags] Vault refund 失败: " + msg);
            }
            return ok;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[DraCaveTags] Vault refund 异常: " + ex.getMessage());
            return false;
        }
    }
}