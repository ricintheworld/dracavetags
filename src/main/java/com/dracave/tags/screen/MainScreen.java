package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.config.GuiConfig;
import com.dracave.tags.config.GuiConfig.IconAction;
import com.dracave.tags.config.GuiConfig.IconDef;
import com.dracave.tags.config.GuiConfig.MenuDef;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class MainScreen implements ClickableScreen {
    private final DraCaveTags plugin;
    private final Player player;
    private final Map<Integer, IconAction> leftActions = new HashMap<>();
    private final Map<Integer, IconAction> rightActions = new HashMap<>();
    private final Map<Integer, IconAction> shiftLeftActions = new HashMap<>();
    private final MenuDef menu;
    private Inventory inventory;

    public MainScreen(DraCaveTags plugin, Player player) {
        this.plugin = plugin; this.player = player;
        GuiConfig gui = plugin.guiConfig();
        this.menu = gui != null ? gui.get("main") : null;
    }

    public void open() {
        String title = menu != null ? menu.title() : "";
        inventory = Bukkit.createInventory(this, menu != null ? menu.size() : 54, MiniMessage.miniMessage().deserialize(title));
        leftActions.clear();
        rightActions.clear();
        shiftLeftActions.clear();
        if (menu != null) {
            for (Map.Entry<Integer, IconDef> entry : menu.slots().entrySet()) {
                int slot = entry.getKey(); IconDef icon = entry.getValue();
                if (icon.permission() != null && !player.hasPermission(icon.permission())) continue;
                inventory.setItem(slot, plugin.guiConfig().buildItem(icon, Map.of()));
                ItemStack item = inventory.getItem(slot);
                if (item != null && item.hasItemMeta()) {
                    ItemMeta m = item.getItemMeta();
                    if (m != null && m.displayName() != null) {
                        m.displayName(m.displayName().decoration(TextDecoration.ITALIC, false));
                        item.setItemMeta(m);
                    }
                }
                if (icon.left() != IconAction.NONE) leftActions.put(slot, icon.left());
                if (icon.right() != IconAction.NONE) rightActions.put(slot, icon.right());
                if (icon.shiftLeft() != IconAction.NONE) shiftLeftActions.put(slot, icon.shiftLeft());
            }
        }
        plugin.screenSound().open(player);
        player.openInventory(inventory);
    }

    @Override public void click(int rawSlot, ClickType clickType) {
        IconAction action = switch (clickType) {
            case RIGHT -> rightActions.getOrDefault(rawSlot, leftActions.getOrDefault(rawSlot, IconAction.NONE));
            case SHIFT_LEFT -> shiftLeftActions.getOrDefault(rawSlot, leftActions.getOrDefault(rawSlot, IconAction.NONE));
            default -> leftActions.getOrDefault(rawSlot, IconAction.NONE);
        };
        if (action == IconAction.NONE) {
            return;
        }
        plugin.screenSound().click(player);
        switch (action) {
            case OPEN_VAULT -> new VaultScreen(plugin, player, 0).open();
            case OPEN_SHOP -> new ShopScreen(plugin, player, 0).open();
            case OPEN_CUSTOM -> new CustomScreen(plugin, player, 0).open();
            case OPEN_REWARD -> new RewardScreen(plugin, player).open();
            case OPEN_ADMIN -> new AdminScreen(plugin, player, 0).open();
            case CLOSE -> player.closeInventory();
            default -> {}
        }
    }

    @Override public Inventory getInventory() { return inventory; }
}
