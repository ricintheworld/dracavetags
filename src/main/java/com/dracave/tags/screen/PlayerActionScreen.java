package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class PlayerActionScreen implements ClickableScreen {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final DraCaveTags plugin;
    private final Player admin;
    private final Player target;
    private Inventory inventory;

    public PlayerActionScreen(DraCaveTags plugin, Player admin, Player target) {
        this.plugin = plugin;
        this.admin = admin;
        this.target = target;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 27,
                MINI.deserialize("<red>管理玩家 <yellow>" + MINI.escapeTags(target.getName())));
        inventory.setItem(11, item(Material.CHEST, "<aqua>管理已有称号",
                "<gray>查看、穿戴或卸下玩家已有称号"));
        inventory.setItem(13, item(Material.FIREWORK_STAR, "<gold>设置聊天颜色",
                "<gray>跟随称号或设置固定颜色"));
        inventory.setItem(15, item(Material.NAME_TAG, "<green>发放新称号",
                "<gray>选择称号并设置有效天数"));
        inventory.setItem(22, item(Material.OAK_DOOR, "<yellow>返回玩家列表"));
        plugin.screenSound().open(admin);
        admin.openInventory(inventory);
    }

    private ItemStack item(Material material, String display, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(MINI.deserialize(display));
        meta.lore(java.util.Arrays.stream(lore).map(MINI::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }

    @Override public void click(int rawSlot, ClickType clickType) {
        if (!target.isOnline()) {
            admin.sendMessage("§c该玩家已经离线。");
            new OnlinePlayerScreen(plugin, admin, 0).open();
            return;
        }
        switch (rawSlot) {
            case 11 -> new ViewScreen(plugin, admin, target.getUniqueId(), target.getName(), 0, true).open();
            case 13 -> new PlayerChatColorScreen(plugin, admin, target).open();
            case 15 -> new PlayerTagScreen(plugin, admin, target, 0).open();
            case 22 -> new OnlinePlayerScreen(plugin, admin, 0).open();
            default -> { }
        }
    }

    @Override public Inventory getInventory() { return inventory; }
}