package com.dracave.tags.economy;

import com.dracave.tags.handlers.EcoType;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.util.UUID;

public final class VaultEco implements EcoProvider {
    private Economy economy;

    public VaultEco() {
        try {
            RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (registration != null) economy = registration.getProvider();
        } catch (NoClassDefFoundError ignored) {}
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
        if (economy == null) return BigDecimal.ZERO;
        try { return BigDecimal.valueOf(economy.getBalance(resolveName(playerId))); }
        catch (Exception ex) { return BigDecimal.ZERO; }
    }

    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        if (economy == null) return false;
        try {
            Player online = Bukkit.getPlayer(playerId);
            EconomyResponse resp = online != null
                    ? economy.withdrawPlayer(online, amount.doubleValue())
                    : economy.withdrawPlayer(Bukkit.getOfflinePlayer(playerId), amount.doubleValue());
            return resp.transactionSuccess();
        } catch (Exception ex) { return false; }
    }

    @Override
    public boolean refund(UUID playerId, BigDecimal amount) {
        if (economy == null) return false;
        try {
            Player online = Bukkit.getPlayer(playerId);
            EconomyResponse resp = online != null
                    ? economy.depositPlayer(online, amount.doubleValue())
                    : economy.depositPlayer(Bukkit.getOfflinePlayer(playerId), amount.doubleValue());
            return resp.transactionSuccess();
        } catch (Exception ex) { return false; }
    }
}
