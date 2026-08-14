package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OnlinePlayerScreen implements ClickableScreen {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final DraCaveTags plugin;
    private final Player admin;
    private final int page;
    private final Map<Integer, Player> slots = new HashMap<>();
    private Inventory inventory;

    public OnlinePlayerScreen(DraCaveTags plugin, Player admin, int page) {
        this.plugin = plugin;
        this.admin = admin;
        this.page = Math.max(0, page);
    }

    public void open() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int pages = Math.max(1, (players.size() + 44) / 45);
        int current = Math.min(page, pages - 1);
        inventory = Bukkit.createInventory(this, 54, MINI.deserialize("<red><bold>玩家称号管理"));
        slots.clear();
        int from = current * 45;
        for (int i = from; i < Math.min(players.size(), from + 45); i++) {
            int slot = i - from;
            Player target = players.get(i);
            slots.put(slot, target);
            inventory.setItem(slot, head(target));
        }
        inventory.setItem(48, item(Material.ARROW, "<yellow>上一页"));
        inventory.setItem(49, item(Material.BARRIER, "<red>返回管理界面"));
        inventory.setItem(50, item(Material.ARROW, "<yellow>下一页"));
        plugin.screenSound().open(admin);
        admin.openInventory(inventory);
    }

    private ItemStack head(Player target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return item;
        meta.setOwningPlayer(target);
        meta.displayName(MINI.deserialize("<aqua>" + MINI.escapeTags(target.getName())));
        meta.lore(List.of(MINI.deserialize("<gray>点击管理该玩家的称号")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material material, String display) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MINI.deserialize(display));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override public void click(int rawSlot, ClickType clickType) {
        if (rawSlot == 48 && page > 0) { new OnlinePlayerScreen(plugin, admin, page - 1).open(); return; }
        if (rawSlot == 50) { new OnlinePlayerScreen(plugin, admin, page + 1).open(); return; }
        if (rawSlot == 49) { new AdminScreen(plugin, admin, 0).open(); return; }
        Player target = slots.get(rawSlot);
        if (target != null && target.isOnline()) new PlayerActionScreen(plugin, admin, target).open();
    }

    @Override public Inventory getInventory() { return inventory; }
}