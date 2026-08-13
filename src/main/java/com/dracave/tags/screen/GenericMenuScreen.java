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

import java.util.HashMap;
import java.util.Map;

/**
 * 通用菜单渲染器。根据 menuKey 从 gui/ 目录加载任意 yml 定义的菜单，
 * 支持全部 IconAction 导航动作。通过 /dctags menu <key> 打开。
 */
public final class GenericMenuScreen implements ClickableScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final DraCaveTags plugin;
    private final Player player;
    private final String menuKey;
    private final Map<Integer, IconAction> leftActions = new HashMap<>();
    private final Map<Integer, IconAction> rightActions = new HashMap<>();
    private final Map<Integer, IconAction> shiftLeftActions = new HashMap<>();
    private Inventory inventory;

    public GenericMenuScreen(DraCaveTags plugin, Player player, String menuKey) {
        this.plugin = plugin;
        this.player = player;
        this.menuKey = menuKey;
    }

    public String menuKey() {
        return menuKey;
    }

    public void open() {
        GuiConfig gui = plugin.guiConfig();
        MenuDef menu = gui != null ? gui.get(menuKey) : null;
        if (menu == null) {
            player.sendMessage("§c菜单 " + menuKey + " 不存在，请检查 gui/" + menuKey + ".yml");
            return;
        }
        inventory = Bukkit.createInventory(this, menu.size(), MINI.deserialize(menu.title()));
        leftActions.clear();
        rightActions.clear();
        shiftLeftActions.clear();
        for (Map.Entry<Integer, IconDef> entry : menu.slots().entrySet()) {
            int slot = entry.getKey();
            IconDef icon = entry.getValue();
            if (icon.permission() != null && !player.hasPermission(icon.permission())) {
                continue;
            }
            ItemStack item = gui.buildItem(icon, Map.of());
            if (item != null && item.hasItemMeta()) {
                ItemMeta m = item.getItemMeta();
                if (m != null && m.displayName() != null) {
                    m.displayName(m.displayName().decoration(TextDecoration.ITALIC, false));
                    item.setItemMeta(m);
                }
            }
            inventory.setItem(slot, item);
            if (icon.left() != IconAction.NONE) leftActions.put(slot, icon.left());
            if (icon.right() != IconAction.NONE) rightActions.put(slot, icon.right());
            if (icon.shiftLeft() != IconAction.NONE) shiftLeftActions.put(slot, icon.shiftLeft());
        }
        if (plugin.screenSound() != null) {
            plugin.screenSound().open(player);
        }
        player.openInventory(inventory);
    }

    @Override
    public void click(int rawSlot, ClickType clickType) {
        IconAction action = switch (clickType) {
            case RIGHT -> rightActions.getOrDefault(rawSlot, leftActions.getOrDefault(rawSlot, IconAction.NONE));
            case SHIFT_LEFT -> shiftLeftActions.getOrDefault(rawSlot, leftActions.getOrDefault(rawSlot, IconAction.NONE));
            default -> leftActions.getOrDefault(rawSlot, IconAction.NONE);
        };
        if (action == IconAction.NONE) {
            return;
        }
        if (plugin.screenSound() != null) {
            plugin.screenSound().click(player);
        }
        dispatch(action);
    }

    private void dispatch(IconAction action) {
        switch (action) {
            case OPEN_VAULT -> new VaultScreen(plugin, player, 0).open();
            case OPEN_SHOP -> new ShopScreen(plugin, player, 0).open();
            case OPEN_CUSTOM -> new CustomScreen(plugin, player, 0).open();
            case OPEN_REWARD -> new RewardScreen(plugin, player).open();
            case OPEN_ADMIN -> new AdminScreen(plugin, player, 0).open();
            case OPEN_MAIN_MENU -> new MainScreen(plugin, player).open();
            case OPEN_RANKING -> player.performCommand("dctags ranking");
            case CLOSE -> player.closeInventory();
            default -> {}
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
