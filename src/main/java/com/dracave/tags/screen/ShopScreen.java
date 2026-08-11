package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.config.GuiConfig.*;
import com.dracave.tags.handlers.*;
import com.dracave.tags.render.DCTagRenderer;
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
import java.util.*;

public final class ShopScreen implements ClickableScreen {
    private static final int PAGE_SIZE = 45;
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final DraCaveTags plugin;
    private final Player player;
    private final int page;
    private final EcoType filter;
    private final Map<Integer, DCTag> slots = new HashMap<>();
    private final Map<Integer, IconAction> actions = new HashMap<>();
    private final RefreshCache refreshCache = new RefreshCache();
    private int headSlot = -1;

    private final MenuDef menu;
    private Inventory inventory;
    private SchedulerUtil.Task refreshTask;

    public ShopScreen(DraCaveTags plugin, Player player, int page) { this(plugin, player, page, null); }
    public ShopScreen(DraCaveTags plugin, Player player, int page, EcoType filter) {
        this.plugin = plugin; this.player = player; this.page = Math.max(0, page); this.filter = filter;
        this.menu = plugin.guiConfig() != null ? plugin.guiConfig().get("shop") : null;
    }

    public void open() {
        refreshCache.clear(); slots.clear(); actions.clear();
        PlayerData data = plugin.tagEngine().getCached(player.getUniqueId());
        if (data == null) { plugin.messages().send(player, "loading"); return; }
        List<DCTag> tags = plugin.registry().all().stream()
                .filter(t -> t.purchaseOffer() != null && !t.shopHidden() && !data.unlocked().contains(t.id())
                        && (filter == null || t.purchaseOffer().currency() == filter))
                .sorted(Comparator.comparingInt(DCTag::order).thenComparing(DCTag::id)).toList();
        int pages = Math.max(1, (tags.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int from = Math.min(page, pages - 1) * PAGE_SIZE;
        inventory = Bukkit.createInventory(this, menu != null ? menu.size() : 54, MINI.deserialize(menu != null ? menu.title() : ""));
        fillStatic(Math.min(page, pages - 1) + 1, pages);
        java.util.List<Integer> cslots = menu != null ? new ArrayList<>(menu.contentSlots()) : new ArrayList<>();
        if (cslots.isEmpty()) { for (int j = 0; j < 54; j++) cslots.add(j); }
        for (int i = from; i < Math.min(tags.size(), from + PAGE_SIZE); i++) {
            int idx = i - from; if (idx >= cslots.size()) break;
            int s = cslots.get(idx); slots.put(s, tags.get(i)); inventory.setItem(s, tagItem(tags.get(i)));
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
            boolean isActive = switch (icon.left()) {
                case FILTER_ALL -> filter == null;
                case FILTER_VAULT -> filter == EcoType.VAULT;
                case FILTER_POINTS -> filter == EcoType.PLAYER_POINTS;
                case FILTER_COIN -> filter == EcoType.COIN;
                case FILTER_ITEM -> filter == EcoType.ITEM;
                default -> false;
            };
            ph.put("{active_f}", isActive ? "<bold>" : "");
            if (icon.material() == Material.PLAYER_HEAD) {
                                headSlot = e.getKey();
                inventory.setItem(e.getKey(), playerHead(player));
            } else {
                inventory.setItem(e.getKey(), plugin.guiConfig().buildItem(icon, ph));
            }
            if (icon.left() != IconAction.NONE) actions.put(e.getKey(), icon.left());
        }
    }

    private ItemStack tagItem(DCTag tag) {
        ItemStack i = new ItemStack(Material.NAME_TAG); ItemMeta m = i.getItemMeta(); if (m == null) return i;
        m.displayName(DCTagRenderer.component(tag, System.currentTimeMillis()).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String d : tag.description()) l.add(MINI.deserialize(d));
        if (!l.isEmpty()) l.add(Component.empty());
        DCTagOffer o = tag.purchaseOffer();
        if (o != null && plugin.shopEngine().currencyAvailable(o)) {
            l.add(plugin.messages().component("gui-price", com.dracave.tags.config.Locale.text("price", o.price().toPlainString()),
                    com.dracave.tags.config.Locale.parsed("currency", o.currency().id())));
            BigDecimal bal = plugin.shopEngine().balance(player.getUniqueId(), o);
            if (bal != null) l.add(plugin.messages().component("gui-balance", com.dracave.tags.config.Locale.text("balance", bal.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()),
                    com.dracave.tags.config.Locale.parsed("currency", o.currency().id())));
            l.add(plugin.messages().component("gui-click-buy"));
        } else l.add(plugin.messages().component("gui-currency-unavailable"));
        if (!tag.permission().isEmpty()) l.add(plugin.messages().component("gui-permission-required", com.dracave.tags.config.Locale.text("permission", tag.permission())));
        m.lore(l); i.setItemMeta(m); return i;
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
                + (ve != null && ve.available() && ve.balance(p.getUniqueId()) != null ? ve.balance(p.getUniqueId()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "不可用")));
        lore.add(MINI.deserialize("<gray>" + ppDisp + ": <white>"
                + (pe != null && pe.available() && pe.balance(p.getUniqueId()) != null ? pe.balance(p.getUniqueId()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "不可用")));
        lore.add(MINI.deserialize("<gray>" + coinDisp + ": <white>"
                + (ce != null && ce.available() && ce.balance(p.getUniqueId()) != null ? ce.balance(p.getUniqueId()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "不可用")));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }


        @Override public void click(int rawSlot, ClickType clickType) {
        plugin.screenSound().click(player);
        switch (actions.getOrDefault(rawSlot, IconAction.NONE)) {
            case OPEN_MAIN_MENU -> {close(); new MainScreen(plugin, player).open();}
            case PAGE_PREV -> {close(); new ShopScreen(plugin, player, page - 1, filter).open();}
            case PAGE_NEXT -> {close(); new ShopScreen(plugin, player, page + 1, filter).open();}
            case FILTER_ALL -> {close(); new ShopScreen(plugin, player, 0, null).open();}
            case FILTER_VAULT -> {close(); new ShopScreen(plugin, player, 0, EcoType.VAULT).open();}
            case FILTER_POINTS -> {close(); new ShopScreen(plugin, player, 0, EcoType.PLAYER_POINTS).open();}
            case FILTER_COIN -> {close(); new ShopScreen(plugin, player, 0, EcoType.COIN).open();}
            case FILTER_ITEM -> {close(); new ShopScreen(plugin, player, 0, EcoType.ITEM).open();}
            case CLOSE -> player.closeInventory();
            default -> { DCTag t = slots.get(rawSlot); if (t != null) { close(); new ConfirmScreen(plugin, player, t, page, ConfirmScreen.ReturnTarget.SHOP).open(); } }
        }
    }
    private void close() { if (refreshTask != null) refreshTask.cancel(); }
    private void refresh() {
        if (inventory == null) return;

            if (headSlot >= 0) {
                ItemStack hi = inventory.getItem(headSlot);
                if (hi != null) {
                    var hm = hi.getItemMeta();
                    if (hm != null) {
                        var lo = new java.util.ArrayList<>(hm.lore());
                        String eqId = plugin.tagEngine().getCached(player.getUniqueId()) != null
                                ? plugin.tagEngine().getCached(player.getUniqueId()).equippedId() : null;
                        var eqTag = eqId != null ? plugin.registry().get(eqId) : null;
                        lo.set(0, MINI.deserialize(eqTag != null
                                ? "<aqua>装备: <white>" + DCTagRenderer.miniMessage(eqTag, System.currentTimeMillis())
                                : "<gray>未装备称号"));
                        hm.lore(lo);
                        hi.setItemMeta(hm);
                        inventory.setItem(headSlot, hi);
                    }
                }
            }
                for (Map.Entry<Integer, DCTag> e : new HashMap<>(slots).entrySet()) {
            DCTag t = e.getValue(); if (t == null) continue;
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
