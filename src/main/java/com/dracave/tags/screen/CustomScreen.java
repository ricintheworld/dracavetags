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
import java.util.*;

public final class CustomScreen implements ClickableScreen {
    private static final int PAGE_SIZE = 45;
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final DraCaveTags plugin;
    private final Player player;
    private final int page;
    private final Map<Integer, CustomDCTag> tagSlots = new HashMap<>();
    private final Map<Integer, IconAction> actions = new HashMap<>();
    private final RefreshCache refreshCache = new RefreshCache();
    private int headSlot = -1;

    private final MenuDef menu;
    private Inventory inventory;
    private SchedulerUtil.Task refreshTask;

    public CustomScreen(DraCaveTags plugin, Player player, int page) {
        this.plugin = plugin; this.player = player; this.page = Math.max(0, page);
        this.menu = plugin.guiConfig() != null ? plugin.guiConfig().get("custom") : null;
    }

    public void open() {
        List<CustomDCTag> owned = plugin.customEngine().ownedBy(player.getUniqueId());
        inventory = Bukkit.createInventory(this, menu != null ? menu.size() : 54, MINI.deserialize(menu != null ? menu.title() : ""));
        actions.clear(); tagSlots.clear(); refreshCache.clear();
        fillStatic(owned.size());
        for (int i = 0, s = 0; s < PAGE_SIZE && i < owned.size(); s++, i++) {
            tagSlots.put(s, owned.get(i)); inventory.setItem(s, tagItem(owned.get(i)));
        }
        plugin.screenSound().open(player); player.openInventory(inventory);
        refreshTask = SchedulerUtil.runTaskTimer(plugin, this::refresh, 2L, 2L);
    }

    private void fillStatic(int used) {
        if (menu == null) return;
        for (Map.Entry<Integer, IconDef> e : menu.slots().entrySet()) {
            if (menu.contentSlots().contains(e.getKey())) continue;
            IconDef icon = e.getValue();
            if (icon.permission() != null && !player.hasPermission(icon.permission())) continue;
            Map<String, String> ph = new HashMap<>();
            ph.put("{used}", Integer.toString(used));
            ph.put("{limit}", Integer.toString(plugin.customEngine().limit(player)));
            if (icon.material() == Material.PLAYER_HEAD) {
                                headSlot = e.getKey();
                inventory.setItem(e.getKey(), playerHead(player));
            } else {
                inventory.setItem(e.getKey(), plugin.guiConfig().buildItem(icon, ph));
            }
            if (icon.left() != IconAction.NONE) actions.put(e.getKey(), icon.left());
        }
    }

    private ItemStack tagItem(CustomDCTag tag) {
        var def = plugin.registry().get(tag.id());
        ItemStack i = new ItemStack(Material.NAME_TAG); ItemMeta m = i.getItemMeta(); if (m == null) return i;
        m.displayName((def != null ? DCTagRenderer.component(def, System.currentTimeMillis()) : Component.text(tag.text())).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(MINI.deserialize("<yellow>左键编辑"), MINI.deserialize("<red>右键删除")));
        i.setItemMeta(m); return i;
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
            case OPEN_VAULT -> {close(); new VaultScreen(plugin, player, 0).open();}
            case OPEN_SHOP -> {close(); new ShopScreen(plugin, player, 0).open();}
            case OPEN_MAIN_MENU -> {close(); new MainScreen(plugin, player).open();}
            case CREATE_CUSTOM -> {close(); player.performCommand("dctags custom create");}
            case CLOSE -> player.closeInventory();
            default -> {
                CustomDCTag t = tagSlots.get(rawSlot); if (t == null) return;
                if (clickType == ClickType.RIGHT) { plugin.customEngine().delete(player, t.id()); player.closeInventory(); }
                else { close(); new StyleScreen(plugin, player).open(); }
            }
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
                for (Map.Entry<Integer, CustomDCTag> e : new HashMap<>(tagSlots).entrySet()) {
            var def = plugin.registry().get(e.getValue().id()); if (def == null) continue;
            String r = DCTagRenderer.miniMessage(def, System.currentTimeMillis());
            if (refreshCache.checkAndUpdate(e.getKey(), r)) {
                ItemStack i = inventory.getItem(e.getKey());
                if (i != null) { ItemMeta m = i.getItemMeta(); if (m != null) { m.displayName(MINI.deserialize(r).decoration(TextDecoration.ITALIC, false)); i.setItemMeta(m); inventory.setItem(e.getKey(), i); } }
            }
        }
    }
    @Override public Inventory getInventory() { return inventory; }

        @Override public void onClose() { close(); }
}
