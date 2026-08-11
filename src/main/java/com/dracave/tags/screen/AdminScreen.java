package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.config.GuiConfig.*;
import com.dracave.tags.handlers.*;
import com.dracave.tags.render.DCTagRenderer;
import com.dracave.tags.util.SchedulerUtil;
import com.dracave.tags.panel.AdminConsole;
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
import java.io.File;
import java.nio.file.Files;
import java.util.*;

public final class AdminScreen implements ClickableScreen {
    private static final int PAGE_SIZE = 45;
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final DraCaveTags plugin;
    private final Player player;
    private final int page;
    private final Map<Integer, String> slots = new HashMap<>();
    private final Map<Integer, IconAction> actions = new HashMap<>();
    private final RefreshCache refreshCache = new RefreshCache();
    private final MenuDef menu;
    private Inventory inventory;
    private SchedulerUtil.Task refreshTask;

    public AdminScreen(DraCaveTags plugin, Player player, int page) {
        this.plugin = plugin; this.player = player; this.page = Math.max(0, page);
        this.menu = plugin.guiConfig() != null ? plugin.guiConfig().get("admin") : null;
    }

    public void open() {
        List<DCTag> tags = plugin.registry().configured();
        int pages = Math.max(1, (tags.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int from = Math.min(page, pages - 1) * PAGE_SIZE;
        inventory = Bukkit.createInventory(this, menu != null ? menu.size() : 54, MINI.deserialize(menu != null ? menu.title() : ""));
        slots.clear(); actions.clear(); refreshCache.clear();
        fillStatic(Math.min(page, pages - 1) + 1, pages);
        for (int i = from; i < Math.min(tags.size(), from + PAGE_SIZE); i++) {
            int s = i - from; slots.put(s, tags.get(i).id()); inventory.setItem(s, tagItem(tags.get(i)));
        }
        plugin.screenSound().open(player); player.openInventory(inventory);
        refreshTask = SchedulerUtil.runTaskTimer(plugin, this::refresh, 2L, 2L);
    }

    private void fillStatic(int curPage, int pages) {
        if (menu == null) return;
        for (Map.Entry<Integer, IconDef> e : menu.slots().entrySet()) {
            if (menu.contentSlots().contains(e.getKey())) continue;
            IconDef icon = e.getValue();
            if (icon.permission() != null && !player.hasPermission(icon.permission())) continue;
            Map<String, String> ph = new HashMap<>();
            ph.put("{page}", Integer.toString(curPage)); ph.put("{pages}", Integer.toString(pages));
            inventory.setItem(e.getKey(), plugin.guiConfig().buildItem(icon, ph));
            if (icon.left() != IconAction.NONE) actions.put(e.getKey(), icon.left());
        }
    }

    private ItemStack tagItem(DCTag tag) {
        ItemStack i = new ItemStack(Material.NAME_TAG); ItemMeta m = i.getItemMeta(); if (m == null) return i;
        m.displayName(DCTagRenderer.component(tag, System.currentTimeMillis()).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        DCTagOffer o = tag.purchaseOffer();
        if (o != null && plugin.shopEngine().currencyAvailable(o)) l.add(MINI.deserialize("<gray>价格: <white>" + o.price().toPlainString()));
        l.add(plugin.messages().component("gui-admin-edit"));
        l.add(MINI.deserialize("<red>Shift+点击删除"));
        m.lore(l); i.setItemMeta(m); return i;
    }

    @Override public void click(int rawSlot, ClickType clickType) {
        plugin.screenSound().click(player);
        switch (actions.getOrDefault(rawSlot, IconAction.NONE)) {
            case OPEN_MAIN_MENU -> {close(); new MainScreen(plugin, player).open();}
            case PAGE_PREV -> {close(); new AdminScreen(plugin, player, page - 1).open();}
            case PAGE_NEXT -> {close(); new AdminScreen(plugin, player, page + 1).open();}
            case COMMAND_UPLOAD -> plugin.defEngine().upload().thenAccept(r -> SchedulerUtil.runTask(plugin, () -> { player.sendMessage("\u00a7a上传: " + r.inserted() + " \u79f0\u53f7"); open(); }));
            case COMMAND_CHECK -> plugin.defEngine().checkUpload().thenAccept(r -> SchedulerUtil.runTask(plugin, () -> {
                player.sendMessage("\u00a7a\u6821\u9a8c: " + r.count() + " \u4e2a\u79f0\u53f7");
                if (r.errors().isEmpty()) player.sendMessage("\u00a7a\u2713 \u5168\u90e8\u901a\u8fc7");
                else r.errors().forEach(e -> player.sendMessage("\u00a7c\u2717 " + e));
                open();
            }));
            case CREATE -> {
                String id = "tag_" + UUID.randomUUID().toString().substring(0, 8);
                close();
                SchedulerUtil.runTaskAsynchronously(plugin, () -> {
                    try {
                        File tagsFile = new File(plugin.getDataFolder(), "tags.yml");
                        String yml = tagsFile.exists() ? java.nio.file.Files.readString(tagsFile.toPath()) : "tags:";
                        if (!yml.contains("tags:")) yml = "tags:\n";
                                String createText = plugin.getConfig().getString("chat.create-title", "[ 称号 ]");
                        yml = yml.trim() + "\n  " + id + ":\n    text: \"" + createText + "\"\n    icon: NAME_TAG\n    order: 0\n";
                        java.nio.file.Files.writeString(tagsFile.toPath(), yml);
                        plugin.defEngine().upload().thenAccept(r -> SchedulerUtil.runTask(plugin, () ->
                            plugin.adminConsole().openEditor(player, id, AdminConsole.EditorReturn.ADMIN_SHOP, page)));
                    } catch (Exception ex) {
                        SchedulerUtil.runTask(plugin, () -> player.sendMessage("\u00a7c创建失败: " + ex.getMessage()));
                    }
                });
            }
            case CLOSE -> player.closeInventory();
            default -> {
                String tid = slots.get(rawSlot); if (tid == null) return;
                if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) { plugin.getServer().dispatchCommand(player, "dctags del " + tid); player.closeInventory(); }
                else { player.closeInventory(); plugin.getServer().dispatchCommand(player, "dctags panel-id " + tid); }
            }
        }
    }
    private void close() { if (refreshTask != null) refreshTask.cancel(); }
    private void refresh() {
        if (inventory == null) return;
        for (Map.Entry<Integer, String> e : new HashMap<>(slots).entrySet()) {
            DCTag t = plugin.registry().get(e.getValue()); if (t == null) continue;
            String r = DCTagRenderer.miniMessage(t, System.currentTimeMillis());
            if (refreshCache.checkAndUpdate(e.getKey(), r)) {
                ItemStack i = inventory.getItem(e.getKey());
                if (i != null) { ItemMeta m = i.getItemMeta(); if (m != null) { m.displayName(MINI.deserialize(r).decoration(TextDecoration.ITALIC, false)); i.setItemMeta(m); inventory.setItem(e.getKey(), i); } }
            }
        }
    }
    @Override public Inventory getInventory() { return inventory; }

        @Override public void onClose() { close(); }
}
