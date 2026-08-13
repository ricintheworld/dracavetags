package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.api.ShopResult;
import com.dracave.tags.api.ShopStatus;
import com.dracave.tags.config.Locale;
import com.dracave.tags.config.Cfg;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.DCTagOffer;
import com.dracave.tags.render.DCTagRenderer;
import com.dracave.tags.util.ItemResolver;
import com.dracave.tags.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.ArrayList;
import java.util.List;

public final class ConfirmScreen implements ClickableScreen {
    private final DraCaveTags plugin;
    private final Player player;
    private final DCTag tag;
    private final int returnPage;
    private final ReturnTarget returnTarget;
    private final int size;
    private final int tagSlot;
    private final int confirmSlot;
    private final int cancelSlot;
    private final int mainMenuSlot;
    private Inventory inventory;
    private boolean processing;
    private String lastRendered;
    private SchedulerUtil.Task refreshTask;

    public ConfirmScreen(DraCaveTags plugin, Player player, DCTag tag,
                         int returnPage, ReturnTarget returnTarget) {
        this.plugin = plugin;
        this.player = player;
        this.tag = tag;
        this.returnPage = returnPage;
        this.returnTarget = returnTarget;
        int configuredSize = plugin.getConfig().getInt("interface.purchase-confirm.size", 27);
        this.size = configuredSize >= 18 && configuredSize <= 54 && configuredSize % 9 == 0 ? configuredSize : 27;
        int configuredTag = plugin.getConfig().getInt("interface.purchase-confirm.title-slot", 13);
        int configuredConfirm = plugin.getConfig().getInt("interface.purchase-confirm.confirm-slot", 11);
        int configuredCancel = plugin.getConfig().getInt("interface.purchase-confirm.cancel-slot", 15);
        if (valid(configuredTag) && valid(configuredConfirm) && valid(configuredCancel)
                && configuredTag != configuredConfirm && configuredTag != configuredCancel
                && configuredConfirm != configuredCancel) {
            this.tagSlot = configuredTag;
            this.confirmSlot = configuredConfirm;
            this.cancelSlot = configuredCancel;
        } else {
            this.tagSlot = 13;
            this.confirmSlot = 11;
            this.cancelSlot = 15;
            plugin.getLogger().warning("购买确认 GUI 槽位无效或冲突，已使用默认布局");
        }
        this.mainMenuSlot = findMainMenuSlot();
    }

    private boolean valid(int slot) {
        return slot >= 0 && slot < size;
    }

    public void open() {
        if (tag.purchaseOffer() == null) {
            return;
        }
        lastRendered = null;
        inventory = Bukkit.createInventory(this, size,
                MiniMessage.miniMessage().deserialize(plugin.getConfig().getString("interface.purchase-confirm.title", "<gold>确认购买称号</gold>")));
        ItemStack pane = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < size; slot++) {
            if (slot != tagSlot && slot != confirmSlot && slot != cancelSlot && slot != mainMenuSlot) {
                inventory.setItem(slot, pane);
            }
        }
        inventory.setItem(tagSlot, tagItem());
        inventory.setItem(confirmSlot, button(material("confirm-material", Material.LIME_CONCRETE), plugin.messages().component("gui-confirm")));
        inventory.setItem(cancelSlot, button(material("cancel-material", Material.RED_CONCRETE), plugin.messages().component("gui-cancel")));
        inventory.setItem(mainMenuSlot, button(Material.OAK_DOOR, plugin.messages().component("gui-back-main")));
        plugin.screenSound().open(player);
        player.openInventory(inventory);
        if (tag.animated()) {
            refreshTask = SchedulerUtil.runTaskTimer(plugin, this::refreshTagItem, 2L, 2L);
        }
    }

    private int findMainMenuSlot() {
        int preferred = size - 5;
        if (preferred != tagSlot && preferred != confirmSlot && preferred != cancelSlot) {
            return preferred;
        }
        for (int slot = size - 1; slot >= 0; slot--) {
            if (slot != tagSlot && slot != confirmSlot && slot != cancelSlot) {
                return slot;
            }
        }
        return 0;
    }

    private ItemStack tagItem() {
        ItemStack item = ItemResolver.resolve(tag.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(DCTagRenderer.component(tag, System.currentTimeMillis()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        tag.description().forEach(line -> lore.add(MiniMessage.miniMessage().deserialize(line).decoration(TextDecoration.ITALIC, false)));
        DCTagOffer offer = tag.purchaseOffer();
        String currency = currencyDisplay();
        lore.add(Component.empty());
        lore.add(plugin.messages().component("gui-price",
                Locale.text("price", offer.price().toPlainString()),
                Locale.parsed("currency", currency)).decoration(TextDecoration.ITALIC, false));
        BigDecimal balance = plugin.shopEngine().balance(player.getUniqueId(), offer);
        if (balance != null) {
            lore.add(plugin.messages().component("gui-balance",
                    Locale.text("balance", balance.stripTrailingZeros().toPlainString()),
                    Locale.parsed("currency", currency)).decoration(TextDecoration.ITALIC, false));
        }
        if (plugin.getConfig().getBoolean(Cfg.SHOP_AUTO_EQUIP, true)) {
            lore.add(plugin.messages().component("gui-auto-equip").decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private Material material(String key, Material fallback) {
        Material value = Material.matchMaterial(plugin.getConfig().getString("interface.purchase-confirm." + key, fallback.name()));
        return value != null && !value.isAir() ? value : fallback;
    }

    private void refreshTagItem() {
        String rendered = DCTagRenderer.miniMessage(tag, System.currentTimeMillis());
        if (rendered.equals(lastRendered)) {
            return;
        }
        lastRendered = rendered;
        ItemStack item = inventory.getItem(tagSlot);
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            meta.displayName(MiniMessage.miniMessage().deserialize(rendered).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
            inventory.setItem(tagSlot, item);
        }
    }

    @Override
    public void onClose() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    @Override
    public void click(int rawSlot, ClickType clickType) {
        if (processing) {
            plugin.screenSound().error(player);
            plugin.messages().send(player, "purchase-in-progress");
        } else if (rawSlot == mainMenuSlot) {
            plugin.screenSound().click(player);
            new MainScreen(plugin, player).open();
        } else if (rawSlot == cancelSlot) {
            plugin.screenSound().click(player);
            returnToOrigin();
        } else if (rawSlot == confirmSlot) {
            plugin.screenSound().click(player);
            DCTag current = plugin.registry().get(tag.id());
            if (current != null && current.purchaseOffer() != null) {
                if (current.revision() == tag.revision() && current.purchaseOffer().equals(tag.purchaseOffer())) {
                    processing = true;
                    plugin.shopEngine().purchase(player.getUniqueId(), tag.id())
                            .whenComplete((result, error) -> SchedulerUtil.runTask(plugin, () -> {
                                if (!player.isOnline()) {
                                    return;
                                }
                                processing = false;
                                if (error == null && result != null) {
                                    if (result.status() == ShopStatus.SUCCESS) {
                                        plugin.screenSound().success(player);
                                    } else {
                                        plugin.screenSound().error(player);
                                    }
                                    sendResult(result);
                                } else {
                                    plugin.screenSound().error(player);
                                    plugin.messages().send(player, "operation-failed");
                                }
                                returnToOrigin();
                            }));
                } else {
                    plugin.screenSound().error(player);
                    player.sendMessage(Component.text("称号价格已更新，请重新确认。", NamedTextColor.YELLOW));
                    new ConfirmScreen(plugin, player, current, returnPage, returnTarget).open();
                }
            } else {
                plugin.screenSound().error(player);
                plugin.messages().send(player, "purchase-not-purchasable");
                returnToOrigin();
            }
        }
    }

    private void sendResult(ShopResult result) {
        String key = switch (result.status()) {
            case SUCCESS -> "purchase-success";
            case ALREADY_UNLOCKED -> "purchase-already-owned";
            case NOT_PURCHASABLE -> "purchase-not-purchasable";
            case PERMISSION_DENIED -> "purchase-permission-denied";
            case CURRENCY_UNAVAILABLE -> "purchase-currency-unavailable";
            case INSUFFICIENT_FUNDS -> "purchase-insufficient-funds";
            case PURCHASE_IN_PROGRESS -> "purchase-in-progress";
            case CANCELLED -> "purchase-cancelled";
            case PAYMENT_FAILED -> "purchase-payment-failed";
            case REFUNDED -> "purchase-refunded";
            case REFUND_PENDING -> "purchase-refund-pending";
            default -> "operation-failed";
        };
        plugin.messages().send(player, key,
                Locale.parsed("title", DCTagRenderer.miniMessage(tag, System.currentTimeMillis())),
                Locale.text("price", result.amount().toPlainString()),
                Locale.parsed("currency", currencyDisplay()),
                Locale.text("operation", result.operationId() == null ? "-" : result.operationId().toString()));
    }

    private String currencyDisplay() {
        DCTagOffer offer = tag.purchaseOffer();
        if (offer.currency() == com.dracave.tags.handlers.EcoType.ITEM) {
            return ItemResolver.displayName(offer.itemMaterial());
        }
        return plugin.getConfig().getString("shop.currencies." + offer.currency().id() + ".display",
                offer.currency().id());
    }

    private void returnToOrigin() {
        if (returnTarget == ReturnTarget.SHOP) {
            new ShopScreen(plugin, player, returnPage).open();
        } else {
            new VaultScreen(plugin, player, returnPage).open();
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public enum ReturnTarget {
        SHOP, WEAR
    }
}
