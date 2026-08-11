package com.dracave.tags.economy;

import com.dracave.tags.handlers.EcoType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.UUID;

public final class ItemEco implements EcoProvider {
    private final String material;

    public ItemEco(String material) {
        this.material = material;
    }

    @Override
    public EcoType type() {
        return EcoType.ITEM;
    }

    @Override
    public boolean available() {
        return material != null && !material.isBlank();
    }

    @Override
    public BigDecimal balance(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return BigDecimal.ZERO;
        }
        Material mat = parseMaterial(material);
        if (mat == null) {
            return BigDecimal.ZERO;
        }
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) {
                count += item.getAmount();
            }
        }
        return BigDecimal.valueOf(count);
    }

    @Override
    public boolean withdraw(UUID playerId, BigDecimal amount) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || amount.scale() > 0 || amount.signum() <= 0) {
            return false;
        }
        Material mat = parseMaterial(material);
        if (mat == null) {
            return false;
        }
        int remaining = amount.intValueExact();
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == mat) {
                int take = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - take);
                remaining -= take;
                if (item.getAmount() <= 0) {
                    contents[i] = null;
                }
            }
        }
        if (remaining > 0) {
            return false;
        }
        player.getInventory().setContents(contents);
        return true;
    }

    @Override
    public boolean refund(UUID playerId, BigDecimal amount) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || amount.scale() > 0 || amount.signum() <= 0) {
            return false;
        }
        Material mat = parseMaterial(material);
        if (mat == null) {
            return false;
        }
        int remaining = amount.intValueExact();
        while (remaining > 0) {
            int stack = Math.min(remaining, mat.getMaxStackSize());
            player.getInventory().addItem(new ItemStack(mat, stack));
            remaining -= stack;
        }
        return true;
    }

    private static Material parseMaterial(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Material.valueOf(name.toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
