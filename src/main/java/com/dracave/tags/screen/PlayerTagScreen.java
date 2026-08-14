package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.render.DCTagRenderer;
import com.dracave.tags.util.ItemResolver;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlayerTagScreen implements ClickableScreen {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final DraCaveTags plugin;
    private final Player admin;
    private final Player target;
    private final int page;
    private final Map<Integer, String> slots = new HashMap<>();
    private Inventory inventory;

    public PlayerTagScreen(DraCaveTags plugin, Player admin, Player target, int page) {
        this.plugin = plugin; this.admin = admin; this.target = target; this.page = Math.max(0, page);
    }

    public void open() {
        List<DCTag> tags = plugin.registry().configured();
        int pages = Math.max(1, (tags.size() + 44) / 45);
        int current = Math.min(page, pages - 1);
        inventory = Bukkit.createInventory(this, 54, MINI.deserialize("<red>为 <yellow>" + MINI.escapeTags(target.getName()) + " <red>选择称号"));
        slots.clear();
        int from = current * 45;
        for (int i = from; i < Math.min(tags.size(), from + 45); i++) {
            int slot = i - from;
            DCTag tag = tags.get(i);
            slots.put(slot, tag.id());
            inventory.setItem(slot, tagItem(tag));
        }
        inventory.setItem(48, simple(Material.ARROW, "<yellow>上一页"));
        inventory.setItem(49, simple(Material.BARRIER, "<red>返回玩家列表"));
        inventory.setItem(50, simple(Material.ARROW, "<yellow>下一页"));
        plugin.screenSound().open(admin); admin.openInventory(inventory);
    }

    private ItemStack tagItem(DCTag tag) {
        ItemStack item = ItemResolver.resolve(tag.icon());
        ItemMeta meta = item.getItemMeta(); if (meta == null) return item;
        meta.displayName(DCTagRenderer.component(tag, System.currentTimeMillis()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(MINI.deserialize("<gray>编号: <white>" + MINI.escapeTags(tag.id())));
        lore.add(MINI.deserialize("<yellow>点击设置发放天数"));
        meta.lore(lore); item.setItemMeta(meta); return item;
    }

    private ItemStack simple(Material material, String display) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.displayName(MINI.deserialize(display)); item.setItemMeta(meta); }
        return item;
    }

    @Override public void click(int rawSlot, ClickType clickType) {
        if (rawSlot == 48 && page > 0) { new PlayerTagScreen(plugin, admin, target, page - 1).open(); return; }
        if (rawSlot == 50) { new PlayerTagScreen(plugin, admin, target, page + 1).open(); return; }
        if (rawSlot == 49) { new PlayerActionScreen(plugin, admin, target).open(); return; }
        String tagId = slots.get(rawSlot);
        if (tagId != null) new GrantDurationScreen(plugin, admin, target, tagId, this).open();
    }

    @Override public Inventory getInventory() { return inventory; }
}