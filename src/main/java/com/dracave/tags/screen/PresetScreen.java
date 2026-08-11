package com.dracave.tags.screen;

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

public final class PresetScreen<T> implements ClickableScreen {
    public record Option<T>(Material icon, String display, String hint, T value) {}

    private final Player player;
    private final String title;
    private final List<Option<T>> options;
    private final Consumer<T> onPick;
    private final Runnable onCancel;
    private final Runnable onManual;
    private final int size;
    private Inventory inventory;

    public PresetScreen(Player player, String title, List<Option<T>> options,
                        Consumer<T> onPick, Runnable onCancel, Runnable onManual) {
        this.player = player;
        this.title = title;
        this.options = options.size() > 45 ? options.subList(0, 45) : options;
        this.onPick = onPick;
        this.onCancel = onCancel;
        this.onManual = onManual;
        this.size = Math.min(54, ((this.options.size() + 8) / 9 + 1) * 9);
    }

    public void open() {
        inventory = Bukkit.createInventory(this, size, MiniMessage.miniMessage().deserialize(title));
        for (int i = 0; i < options.size(); i++) {
            Option<T> option = options.get(i);
            ItemStack item = new ItemStack(option.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(MiniMessage.miniMessage().deserialize(option.display()).decoration(TextDecoration.ITALIC, false));
            if (option.hint() != null) {
                meta.lore(List.of(MiniMessage.miniMessage().deserialize("<dark_gray>" + option.hint()).decoration(TextDecoration.ITALIC, false)));
            }
            item.setItemMeta(meta);
            inventory.setItem(i, item);
        }
        inventory.setItem(size - 9, button(Material.ARROW, "<yellow>返回"));
        if (onManual != null) {
            inventory.setItem(size - 5, button(Material.WRITABLE_BOOK, "<yellow>手动输入"));
        }
        player.openInventory(inventory);
    }

    private ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(name).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void click(int rawSlot, ClickType clickType) {
        if (rawSlot == size - 9) {
            onCancel.run();
        } else if (onManual != null && rawSlot == size - 5) {
            onManual.run();
        } else if (rawSlot >= 0 && rawSlot < options.size()) {
            onPick.accept(options.get(rawSlot).value());
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
