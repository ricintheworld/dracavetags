package com.dracave.tags.economy;

import com.dracave.tags.handlers.EcoType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.util.UUID;

public final class VaultEco implements EcoProvider {
    private Economy economy;

    public VaultEco() {
        try {
            RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (registration != null) {
                economy = registration.getProvider();
            }
        } catch (NoClassDefFoundError ignored) {
        }
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
        if (economy == null) {
            return BigDecimal.ZERO;
        }
        try {
            return BigDecimal.valueOf(economy.getBalance(Bukkit.getOfflinePlayer(playerId)));
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        if (economy == null) {
            return false;
        }
        try {
            return economy.withdrawPlayer(Bukkit.getOfflinePlayer(playerId), amount.doubleValue()).transactionSuccess();
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public boolean refund(UUID playerId, BigDecimal amount) {
        if (economy == null) {
            return false;
        }
        try {
            return economy.depositPlayer(Bukkit.getOfflinePlayer(playerId), amount.doubleValue()).transactionSuccess();
        } catch (Exception ex) {
            return false;
        }
    }
}
