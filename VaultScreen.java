package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.config.GuiConfig.*;
import com.dracave.tags.config.GuiConfig;
import com.dracave.tags.handlers.*;
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
import java.util.*;

public final class VaultScreen implements ClickableScreen {
    private static final int PAGE_SIZE = 45;
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final DraCaveTags plugin;
    private final Player viewer;
    private final int page;
    private final Map<Integer, String> tagSlots = new HashMap<>();
    private final Map<Integer, IconAction> actions = new HashMap<>();
    private final RefreshCache refreshCache = new RefreshCache();
    private final MenuDef menu;
    private Inventory inventory;
    private SchedulerUtil.Task refreshTask;

    public VaultScreen(DraCaveTags plugin, Player viewer, int page) {
        this.plugin = plugin; this.viewer = viewer; this.page = Math.max(0, page);
        this.menu = plugin.guiConfig() != null ? plugin.guiConfig().get("self") : null;
    }

    public void open() {
        PlayerData data = plugin.tagEngine().getCached(viewer.getUniqueId());
        if (data == null) { plugin.messages().send(viewer, "loading"); return; }
        String title = menu != null ? menu.title() : "";
        inventory = Bukkit.createInventory(this, menu != null ? menu.size() : 54, MINI.deserialize(title));
        actions.clear(); tagSlots.clear(); refreshCache.clear();
        List<DCTag> tags = data.unlocked().stream().map(plugin.registry()::get).filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(DCTag::order).thenComparing(DCTag::id)).toList();
        int pages = Math.max(1, (tags.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int from = Math.min(page, pages - 1) * PAGE_SIZE;
        int to = Math.min(tags.size(), from + PAGE_SIZE);
        fillStatic(data.unlocked().size(), Math.min(page, pages - 1) + 1, pages);
        for (int i = from; i < to; i++) {
            DCTag t = tags.get(i); int s = i - from;
            inventory.setItem(s, tagItem(t, t.id().equals(data.equippedId())));
            tagSlots.put(s, t.id());
        }
        plugin.screenSound().open(viewer); viewer.openInventory(inventory);
        refreshTask = SchedulerUtil.runTaskTimer(plugin, this::refresh, 2L, 2L);
    }

    private void fillStatic(int count, int curPage, int pages) {
        if (menu == null) return;
        for (Map.Entry<Integer, IconDef> e : menu.slots().entrySet()) {
            if (menu.contentSlots().contains(e.getKey())) continue;
            IconDef icon = e.getValue();
            if (icon.permission() != null && !viewer.hasPermission(icon.permission())) continue;
            Map<String, String> ph = new HashMap<>();
            ph.put("{count}", Integer.toString(count));
            ph.put("{page}", Integer.toString(curPage));
            ph.put("{pages}", Integer.toString(pages));
            if (icon.material() == Material.PLAYER_HEAD) {
                inventory.setItem(e.getKey(), playerHead(viewer));
            } else {
                inventory.setItem(e.getKey(), plugin.guiConfig().buildItem(icon, ph));
            }
            if (icon.left() != IconAction.NONE) actions.put(e.getKey(), icon.left());
        }
    }

    private ItemStack tagItem(DCTag tag, boolean equipped) {
        ItemStack item = ItemResolver.resolve(tag.icon());
        ItemMeta m = item.getItemMeta(); if (m == null) return item;
        m.displayName(DCTagRenderer.component(tag, System.currentTimeMillis()).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String d : tag.description()) l.add(MINI.deserialize(d).decoration(TextDecoration.ITALIC, false));
        if (!l.isEmpty()) l.add(Component.empty());
        l.add(plugin.messages().component(equipped ? "gui-equipped" : "gui-unlocked"));
        l.add(plugin.messages().component(equipped ? "gui-click-clear" : "gui-click-equip"));
        m.lore(l); item.setItemMeta(m); return item;
    }


    private ItemStack playerHead(Player p) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        var meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        if (meta == null) return head;
        meta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(p.getUniqueId()));
        meta.displayName(MINI.deserialize(p.getName()));
        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        String eqId = plugin.tagEngine().getCached(p.getUniqueId()) != null
                ? plugin.tagEngine().getCached(p.getUniqueId()).equippedId() : null;
        var eqTag = eqId != null ? plugin.registry().get(eqId) : null;
        lore.add(MINI.deserialize(eqTag != null
                ? "<aqua>装备: <white>" + DCTagRenderer.miniMessage(eqTag, System.currentTimeMillis())
                : "<gray>未装备称号"));
        String vaultDisp = plugin.getConfig().getString("shop.currencies.vault.display", "金币");
        String ppDisp = plugin.getConfig().getString("shop.currencies.playerpoints.display", "点券");
        String coinDisp = plugin.getConfig().getString("shop.currencies.coin.display", "称号币");
        var ve = plugin.currencies().get(EcoType.VAULT);
        var pe = plugin.currencies().get(EcoType.PLAYER_POINTS);
        var ce = plugin.currencies().get(EcoType.COIN);
        lore.add(MINI.deserialize("<gray>" + vaultDisp + ": <white>"
                + (ve != null && ve.available() ? ve.balance(p.getUniqueId()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "不可用")));
        lore.add(MINI.deserialize("<gray>" + ppDisp + ": <white>"
                + (pe != null && pe.available() ? pe.balance(p.getUniqueId()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "不可用")));
        lore.add(MINI.deserialize("<gray>" + coinDisp + ": <white>"
                + (ce != null && ce.available() ? ce.balance(p.getUniqueId()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "不可用")));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }


        @Override public void click(int rawSlot, ClickType clickType) {
        plugin.screenSound().click(viewer);
        switch (actions.getOrDefault(rawSlot, IconAction.NONE)) {
            case OPEN_VAULT -> {close(); new VaultScreen(plugin, viewer, 0).open();}
            case OPEN_SHOP -> {close(); new ShopScreen(plugin, viewer, 0).open();}
            case OPEN_CUSTOM -> {close(); new CustomScreen(plugin, viewer, 0).open();}
            case OPEN_MAIN_MENU -> {close(); new MainScreen(plugin, viewer).open();}
            case OPEN_RANKING -> {close(); plugin.getServer().dispatchCommand(viewer, "dctags ranking");}
            case PAGE_PREV -> {close(); new VaultScreen(plugin, viewer, page - 1).open();}
            case PAGE_NEXT -> {close(); new VaultScreen(plugin, viewer, page + 1).open();}
            case CLOSE -> viewer.closeInventory();
            default -> {
                String tid = tagSlots.get(rawSlot);
                if (tid != null) {
                    PlayerData d = plugin.tagEngine().getCached(viewer.getUniqueId());
                    close();
                    if (d != null && tid.equals(d.equippedId())) plugin.tagEngine().clear(viewer.getUniqueId());
                    else plugin.tagEngine().equip(viewer.getUniqueId(), tid);
                }
            }
        }
    }

    private void close() { if (refreshTask != null) refreshTask.cancel(); }
    private void refresh() {
        if (inventory == null) return;
        for (Map.Entry<Integer, String> e : tagSlots.entrySet()) {
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
