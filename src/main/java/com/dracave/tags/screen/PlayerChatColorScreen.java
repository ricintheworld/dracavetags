package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.engine.ChatColorEngine;
import com.dracave.tags.handlers.ChatColorMode;
import com.dracave.tags.handlers.ChatColorPreference;
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

import java.util.ArrayList;
import java.util.List;

public final class PlayerChatColorScreen implements ClickableScreen {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final DraCaveTags plugin;
    private final Player admin;
    private final Player target;
    private Inventory inventory;

    public PlayerChatColorScreen(DraCaveTags plugin, Player admin, Player target) {
        this.plugin = plugin;
        this.admin = admin;
        this.target = target;
    }

    public void open() {
        ChatColorEngine engine = plugin.chatColorEngine();
        if (engine == null) {
            admin.sendMessage("§c聊天颜色服务当前不可用。");
            return;
        }
        inventory = Bukkit.createInventory(this, 27,
                MINI.deserialize("<gold>聊天颜色 <yellow>" + MINI.escapeTags(target.getName())));
        ChatColorPreference preference = engine.preference(target.getUniqueId());
        String effective = engine.colorHex(target.getUniqueId());

        inventory.setItem(10, option(Material.NAME_TAG, "<aqua>跟随称号颜色", preference.mode() == ChatColorMode.TITLE,
                "<gray>玩家切换称号后自动更新", effective.isEmpty() ? null : "<gray>当前颜色: <" + effective + ">" + effective));
        inventory.setItem(13, option(Material.FIREWORK_STAR, "<green>设置固定聊天颜色",
                preference.mode() == ChatColorMode.CUSTOM,
                "<gray>从调色板选择或输入 HEX", preference.customColor() == null
                        ? null : "<gray>当前颜色: <" + preference.customColor() + ">" + preference.customColor()));
        inventory.setItem(16, option(Material.GRAY_DYE, "<white>使用 TrChat 默认颜色",
                preference.mode() == ChatColorMode.DEFAULT, "<gray>颜色变量返回空文本"));
        inventory.setItem(22, item(Material.OAK_DOOR, "<yellow>返回玩家管理"));
        plugin.screenSound().open(admin);
        admin.openInventory(inventory);
    }

    private ItemStack option(Material material, String display, boolean selected, String... lines) {
        List<String> lore = new ArrayList<>();
        for (String line : lines) {
            if (line != null) {
                lore.add(line);
            }
        }
        lore.add(selected ? "<green>当前正在使用" : "<yellow>点击选择");
        return item(material, display, lore.toArray(String[]::new));
    }

    private ItemStack item(Material material, String display, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(MINI.deserialize(display).decoration(TextDecoration.ITALIC, false));
        List<Component> components = java.util.Arrays.stream(lore)
                .map(MINI::deserialize)
                .map(component -> component.decoration(TextDecoration.ITALIC, false))
                .toList();
        meta.lore(components);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void click(int rawSlot, ClickType clickType) {
        if (!target.isOnline()) {
            admin.sendMessage("§c该玩家已经离线。");
            new OnlinePlayerScreen(plugin, admin, 0).open();
            return;
        }
        switch (rawSlot) {
            case 10 -> saveMode(ChatColorMode.TITLE, "§a已设置为跟随称号颜色。");
            case 13 -> new ColorScreen(plugin, admin,
                    color -> plugin.chatColorEngine().setCustom(target.getUniqueId(), color)
                            .thenAccept(success -> SchedulerUtil.runTask(plugin, () -> finish(success,
                                    "§a已将 " + target.getName() + " 的聊天颜色设置为 " + color + "。"))),
                    this::open).open();
            case 16 -> saveMode(ChatColorMode.DEFAULT, "§a已恢复使用 TrChat 默认聊天颜色。");
            case 22 -> new PlayerActionScreen(plugin, admin, target).open();
            default -> { }
        }
    }

    private void saveMode(ChatColorMode mode, String successMessage) {
        plugin.chatColorEngine().setMode(target.getUniqueId(), mode)
                .thenAccept(success -> SchedulerUtil.runTask(plugin, () -> finish(success, successMessage)));
    }

    private void finish(boolean success, String successMessage) {
        if (!admin.isOnline()) {
            return;
        }
        if (success) {
            plugin.screenSound().success(admin);
            admin.sendMessage(successMessage);
            open();
        } else {
            plugin.screenSound().error(admin);
            admin.sendMessage("§c聊天颜色保存失败，请查看服务器日志。");
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}