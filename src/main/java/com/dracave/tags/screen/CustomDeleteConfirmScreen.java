package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.config.Cfg;
import com.dracave.tags.engine.CustomEngine;
import com.dracave.tags.economy.EcoProvider;
import com.dracave.tags.handlers.CustomDCTag;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.EcoType;
import com.dracave.tags.render.DCTagRenderer;
import com.dracave.tags.util.ItemResolver;
import com.dracave.tags.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public final class CustomDeleteConfirmScreen implements ClickableScreen {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final DraCaveTags plugin;
    private final Player player;
    private final CustomDCTag tag;
    private Inventory inventory;

    public CustomDeleteConfirmScreen(DraCaveTags plugin, Player player, CustomDCTag tag) {
        this.plugin = plugin;
        this.player = player;
        this.tag = tag;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 9, MINI.deserialize("<red>确认删除自定义称号</red>"));

        for (int i = 0; i < 9; i++) {
            if (i != 3 && i != 5) {
                inventory.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
            }
        }

        // 确认按钮
        inventory.setItem(3, button(Material.LIME_CONCRETE, "<green><bold>确认删除</bold></green>",
                List.of(MINI.deserialize("<gray>将删除该自定义称号"),
                        MINI.deserialize("<gray>删除后将释放一个额度"))));
        // 取消按钮
        inventory.setItem(5, button(Material.RED_CONCRETE, "<red><bold>取消</bold></red>",
                List.of(MINI.deserialize("<gray>返回自定义称号界面"))));

        player.openInventory(inventory);
    }

    private ItemStack button(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(MINI.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void click(int rawSlot, ClickType clickType) {
        if (rawSlot == 3) {
            confirmDelete();
        } else if (rawSlot == 5) {
            new CustomScreen(plugin, player, 0).open();
        }
    }

    private void confirmDelete() {
        player.closeInventory();

        // 检查删除花费
        boolean costEnabled = plugin.getConfig().getBoolean(Cfg.CUSTOM_DELETE_COST_ENABLED, false);
        BigDecimal costAmount = BigDecimal.ZERO;
        EcoType costType = null;
        EcoProvider costProvider = null;

        if (costEnabled) {
            try {
                costAmount = new BigDecimal(plugin.getConfig().getString(Cfg.CUSTOM_DELETE_COST_AMOUNT, "0"));
            } catch (NumberFormatException ignored) {
                costAmount = BigDecimal.ZERO;
            }
            if (costAmount.compareTo(BigDecimal.ZERO) > 0) {
                String typeStr = plugin.getConfig().getString(Cfg.CUSTOM_DELETE_COST_TYPE, "vault");
                costType = EcoType.parse(typeStr);
                costProvider = plugin.currencies() != null ? plugin.currencies().get(costType) : null;
                if (costProvider == null || !costProvider.available()) {
                    player.sendMessage("§c货币服务不可用，无法删除称号。");
                    new CustomScreen(plugin, player, 0).open();
                    return;
                }
                BigDecimal balance = costProvider.balance(player.getUniqueId());
                if (balance == null || balance.compareTo(costAmount) < 0) {
                    String currencyName = plugin.getConfig().getString("shop.currencies." + costType.id() + ".display", costType.id());
                    player.sendMessage("§c删除自定义称号需要 " + costAmount.toPlainString() + " " + currencyName + "，你的余额不足。");
                    new CustomScreen(plugin, player, 0).open();
                    return;
                }
                boolean withdrawn = costProvider.withdraw(player.getUniqueId(), costAmount);
                if (!withdrawn) {
                    player.sendMessage("§c扣费失败，请稍后重试。");
                    new CustomScreen(plugin, player, 0).open();
                    return;
                }
            } else {
                costEnabled = false;
            }
        }

        final boolean didCharge = costEnabled && costAmount.compareTo(BigDecimal.ZERO) > 0;
        final BigDecimal chargedAmount = costAmount;
        final EcoProvider chargedProvider = costProvider;

        plugin.customEngine().delete(player, tag.id()).thenAccept(result ->
                SchedulerUtil.runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        if (didCharge && chargedProvider != null) {
                            chargedProvider.refund(player.getUniqueId(), chargedAmount);
                        }
                        return;
                    }
                    if (result == CustomEngine.Result.SUCCESS) {
                        plugin.screenSound().success(player);
                        plugin.messages().send(player, "custom-deleted");
                    } else {
                        if (didCharge && chargedProvider != null) {
                            chargedProvider.refund(player.getUniqueId(), chargedAmount);
                            plugin.messages().send(player, "custom-delete-cost-refunded");
                        }
                        plugin.screenSound().error(player);
                        plugin.messages().send(player, "custom-result-" + result.name().toLowerCase(Locale.ROOT).replace('_', '-'));
                    }
                    new CustomScreen(plugin, player, 0).open();
                }));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void onClose() {
    }
}