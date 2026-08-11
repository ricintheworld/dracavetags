package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.api.DCTagResult;
import com.dracave.tags.config.Locale;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.PlayerData;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ViewScreen implements ClickableScreen {
    private static final int PAGE_SIZE = 45;

    private final DraCaveTags plugin;
    private final Player viewer;
    private final UUID targetId;
    private final String targetName;
    private final int page;
    private final Map<Integer, String> tagSlots = new HashMap<>();
    private final RefreshCache refreshCache = new RefreshCache();
    private Inventory inventory;
    private SchedulerUtil.Task refreshTask;

    public ViewScreen(DraCaveTags plugin, Player viewer, UUID targetId, String targetName, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetId = targetId;
        this.targetName = targetName;
        this.page = Math.max(0, page);
    }

    public void open() {
        PlayerData data = plugin.tagEngine().getCached(targetId);
        if (data == null) {
            plugin.tagEngine().load(targetId).thenAccept(loaded -> SchedulerUtil.runTask(plugin, () -> {
                if (loaded == null) {
                    plugin.messages().send(viewer, "unavailable");
                } else {
                    open();
                }
            }));
            return;
        }
        refreshCache.clear();
        List<DCTag> tags = data.unlocked().stream()
                .map(plugin.registry()::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(DCTag::order).thenComparing(DCTag::id))
                .toList();
        int pages = Math.max(1, (tags.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int actualPage = Math.min(page, pages - 1);
        inventory = Bukkit.createInventory(this, 54, MiniMessage.miniMessage().deserialize("<red>查看玩家称号</red> <gray>" + targetName));
        for (int index = actualPage * PAGE_SIZE; index < Math.min(tags.size(), (actualPage + 1) * PAGE_SIZE); index++) {
            DCTag tag = tags.get(index);
            boolean equipped = tag.id().equals(data.equippedId());
            inventory.setItem(index - actualPage * PAGE_SIZE, tagItem(tag, equipped));
            tagSlots.put(index - actualPage * PAGE_SIZE, tag.id());
        }
        if (tags.isEmpty()) {
            inventory.setItem(22, button(Material.BARRIER, plugin.messages().component("gui-empty")));
        }
        if (actualPage > 0) {
            inventory.setItem(45, button(Material.ARROW, plugin.messages().component("gui-previous")));
        }
        inventory.setItem(48, button(Material.PAPER, plugin.messages().component("gui-status",
                Locale.text("page", Integer.toString(actualPage + 1)),
                Locale.text("pages", Integer.toString(pages)))));
        if (actualPage + 1 < pages) {
            inventory.setItem(53, button(Material.ARROW, plugin.messages().component("gui-next")));
        }
        inventory.setItem(49, button(Material.OAK_DOOR, plugin.messages().component("gui-back-main")));
        plugin.screenSound().open(viewer);
        viewer.openInventory(inventory);
        refreshTask = SchedulerUtil.runTaskTimer(plugin, this::refreshTitleItems, 2L, 2L);
    }

    private ItemStack tagItem(DCTag tag, boolean equipped) {
        ItemStack item = ItemResolver.resolve(tag.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(DCTagRenderer.component(tag, System.currentTimeMillis()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : tag.description()) {
            lore.add(MiniMessage.miniMessage().deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        lore.add(plugin.messages().component(equipped ? "gui-equipped" : "gui-unlocked").decoration(TextDecoration.ITALIC, false));
        lore.add(MiniMessage.miniMessage().deserialize(equipped ? "<yellow>点击为玩家卸下</yellow>" : "<yellow>点击为玩家穿戴</yellow>")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
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

    private void refreshTitleItems() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, String> entry : tagSlots.entrySet()) {
            DCTag tag = plugin.registry().get(entry.getValue());
            if (tag == null || !tag.animated()) {
                continue;
            }
            int slot = entry.getKey();
            String rendered = DCTagRenderer.miniMessage(tag, now);
            if (refreshCache.checkAndUpdate(slot, rendered)) {
                ItemStack item = inventory.getItem(slot);
                if (item != null) {
                    ItemMeta meta = item.getItemMeta();
                    meta.displayName(MiniMessage.miniMessage().deserialize(rendered).decoration(TextDecoration.ITALIC, false));
                    item.setItemMeta(meta);
                    inventory.setItem(slot, item);
                }
            }
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
        if (rawSlot == 49) {
            plugin.screenSound().click(viewer);
            new AdminScreen(plugin, viewer, 0).open();
        } else if (rawSlot == 45 && page > 0) {
            plugin.screenSound().switchPage(viewer);
            new ViewScreen(plugin, viewer, targetId, targetName, page - 1).open();
        } else if (rawSlot == 53) {
            PlayerData current = plugin.tagEngine().getCached(targetId);
            int count = current == null ? 0 : (int) current.unlocked().stream().map(plugin.registry()::get).filter(Objects::nonNull).count();
            int pages = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page + 1 < pages) {
                plugin.screenSound().switchPage(viewer);
                new ViewScreen(plugin, viewer, targetId, targetName, page + 1).open();
            } else {
                plugin.screenSound().error(viewer);
            }
        } else {
            String tagId = tagSlots.get(rawSlot);
            if (tagId != null) {
                PlayerData data = plugin.tagEngine().getCached(targetId);
                if (data == null || !data.unlocked().contains(tagId)) {
                    plugin.screenSound().error(viewer);
                    return;
                }
                boolean unequip = tagId.equals(data.equippedId());
                CompletableFuture<DCTagResult> operation = unequip
                        ? plugin.tagEngine().clear(targetId)
                        : plugin.tagEngine().equip(targetId, tagId);
                operation.thenAccept(result -> SchedulerUtil.runTask(plugin, () -> {
                    if (viewer.isOnline() && result == DCTagResult.SUCCESS) {
                        plugin.screenSound().success(viewer);
                        new ViewScreen(plugin, viewer, targetId, targetName, page).open();
                    } else if (viewer.isOnline()) {
                        plugin.screenSound().error(viewer);
                    }
                }));
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
