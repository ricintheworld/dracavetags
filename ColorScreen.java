package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
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

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class ColorScreen implements ClickableScreen {
    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final int SLOT_MANUAL = 22;
    private static final int SLOT_CANCEL = 18;

    private static final String[][] PALETTE = {
            {"#FFFFFF", "白", "WHITE_DYE"},
            {"#AAAAAA", "淡灰", "LIGHT_GRAY_DYE"},
            {"#555555", "深灰", "GRAY_DYE"},
            {"#191919", "黑", "BLACK_DYE"},
            {"#FF5555", "红", "RED_DYE"},
            {"#FFAA00", "橙", "ORANGE_DYE"},
            {"#FFFF55", "黄", "YELLOW_DYE"},
            {"#55FF55", "黄绿", "LIME_DYE"},
            {"#00AA00", "绿", "GREEN_DYE"},
            {"#00AAAA", "青", "CYAN_DYE"},
            {"#55FFFF", "天蓝", "LIGHT_BLUE_DYE"},
            {"#5555FF", "蓝", "BLUE_DYE"},
            {"#AA00FF", "紫", "PURPLE_DYE"},
            {"#FF55FF", "品红", "MAGENTA_DYE"},
            {"#FFAACC", "粉", "PINK_DYE"},
            {"#A0522D", "棕", "BROWN_DYE"},
    };

    private final DraCaveTags plugin;
    private final Player player;
    private final Consumer<String> onPick;
    private final Runnable onCancel;
    private Inventory inventory;

    public ColorScreen(DraCaveTags plugin, Player player, Consumer<String> onPick, Runnable onCancel) {
        this.plugin = plugin;
        this.player = player;
        this.onPick = onPick;
        this.onCancel = onCancel;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 27, MiniMessage.miniMessage().deserialize("<yellow>选择颜色</yellow>"));
        for (int i = 0; i < PALETTE.length; i++) {
            String[] entry = PALETTE[i];
            Material material = Material.matchMaterial(entry[2]);
            ItemStack item = new ItemStack(material == null ? Material.WHITE_DYE : material);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(MiniMessage.miniMessage().deserialize("<" + entry[0] + ">" + entry[1]).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(MiniMessage.miniMessage().deserialize("<dark_gray>" + entry[0]).decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
            inventory.setItem(i, item);
        }
        inventory.setItem(SLOT_CANCEL, plain(Material.ARROW, "<yellow>返回"));
        inventory.setItem(SLOT_MANUAL, plain(Material.WRITABLE_BOOK, "<yellow>手动输入 #RRGGBB"));
        player.openInventory(inventory);
    }

    private ItemStack plain(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.<Component>of());
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void click(int rawSlot, ClickType clickType) {
        if (rawSlot == SLOT_CANCEL) {
            onCancel.run();
        } else if (rawSlot == SLOT_MANUAL) {
            player.closeInventory();
            plugin.chatPrompt().prompt(player, "§e请输入颜色（#RRGGBB）：", (p, value) -> {
                String color = value.trim().toUpperCase();
                if (!HEX.matcher(color).matches()) {
                    p.sendMessage("§c颜色必须是 #RRGGBB。");
                    onCancel.run();
                    return;
                }
                onPick.accept(color);
            }, true);
        } else if (rawSlot >= 0 && rawSlot < PALETTE.length) {
            onPick.accept(PALETTE[rawSlot][0]);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
