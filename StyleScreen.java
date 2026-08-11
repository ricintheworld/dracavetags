package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.engine.CustomEngine;
import com.dracave.tags.handlers.CustomDraft;
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
import java.util.function.BiFunction;

public final class StyleScreen implements ClickableScreen {
    private static final int SLOT_BACK = 49;
    private static final int[] STATIC_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] DYNAMIC_SLOTS = {28, 29, 30, 31, 32, 33, 34};

    private static final List<Preset> STATIC_PRESETS = List.of(
            solid("天青", Material.LIGHT_BLUE_DYE, "#55FFFF"),
            solid("烈焰红", Material.RED_DYE, "#FF5555"),
            solid("苔原绿", Material.LIME_DYE, "#55FF55"),
            solid("鎏金", Material.ORANGE_DYE, "#FFAA00"),
            solid("魅紫", Material.PURPLE_DYE, "#AA00FF"),
            solid("皓白", Material.WHITE_DYE, "#FFFFFF"),
            solid("墨灰", Material.GRAY_DYE, "#9E9E9E"));

    private static final List<Preset> DYNAMIC_PRESETS = List.of(
            new Preset("<rainbow>七彩虹光</rainbow>", Material.NETHER_STAR, true,
                    (text, period) -> CustomDraft.rainbow(text, period, "NETHER_STAR")),
            gradient("烈焰流光", Material.BLAZE_POWDER, "#FFE259", "#FFA751", "#FF4E00"),
            gradient("极地冰霜", Material.ICE, "#74EBD5", "#9FACE6"),
            gradient("黄金脉动", Material.GOLD_INGOT, "#FFD700", "#FFF8DC", "#FFA500"),
            gradient("剧毒", Material.SLIME_BALL, "#A8FF78", "#78FFD6"),
            flash("深渊紫电", Material.AMETHYST_SHARD, "#8E2DE2", "#4A00E0"),
            flash("血月", Material.REDSTONE, "#FF3030", "#7A0000"));

    private final DraCaveTags plugin;
    private final Player player;
    private Inventory inventory;

    public StyleScreen(DraCaveTags plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 54, MiniMessage.miniMessage().deserialize("<light_purple>选择称号样式</light_purple>"));
        boolean canStatic = player.hasPermission("dracave.tags.custom.static");
        boolean canDynamic = player.hasPermission("dracave.tags.custom.dynamic");
        inventory.setItem(4, button(Material.PAPER, "<yellow>静态样式",
                canStatic ? "<gray>点击后输入文本即可创建" : "<red>你没有创建静态称号的权限"));
        inventory.setItem(22, button(Material.PAPER, "<yellow>动态样式",
                canDynamic ? "<gray>点击后输入文本即可创建" : "<red>你没有创建动态称号的权限"));
        render(STATIC_SLOTS, STATIC_PRESETS, canStatic);
        render(DYNAMIC_SLOTS, DYNAMIC_PRESETS, canDynamic);
        inventory.setItem(SLOT_BACK, button(Material.ARROW, "<yellow>返回"));
        plugin.screenSound().open(player);
        player.openInventory(inventory);
    }

    private void render(int[] slots, List<Preset> presets, boolean allowed) {
        for (int i = 0; i < slots.length && i < presets.size(); i++) {
            Preset preset = presets.get(i);
            inventory.setItem(slots[i], allowed
                    ? button(preset.icon, preset.display, "<dark_gray>点击创建")
                    : button(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>" + strip(preset.display), "<red>权限不足"));
        }
    }

    @Override
    public void click(int rawSlot, ClickType clickType) {
        if (rawSlot == SLOT_BACK) {
            plugin.screenSound().click(player);
            new CustomScreen(plugin, player, 0).open();
            return;
        }
        Preset preset = presetAt(STATIC_SLOTS, STATIC_PRESETS, rawSlot);
        if (preset == null) {
            preset = presetAt(DYNAMIC_SLOTS, DYNAMIC_PRESETS, rawSlot);
        }
        if (preset == null) {
            return;
        }
        if (!player.hasPermission(preset.dynamic ? "dracave.tags.custom.dynamic" : "dracave.tags.custom.static")) {
            plugin.screenSound().error(player);
            plugin.messages().send(player, "custom-result-no-permission");
            return;
        }
        plugin.screenSound().click(player);
        create(preset);
    }

    private static Preset presetAt(int[] slots, List<Preset> presets, int rawSlot) {
        for (int i = 0; i < slots.length && i < presets.size(); i++) {
            if (slots[i] == rawSlot) {
                return presets.get(i);
            }
        }
        return null;
    }

    private void create(Preset preset) {
        player.closeInventory();
        plugin.chatPrompt().prompt(player,
                "§e样式：§r" + strip(preset.display) + "\n§e请输入称号文本（纯文本，直接发送）：",
                (p, text) -> plugin.customEngine().create(p, preset.build.apply(text, period()))
                        .thenAccept(result -> SchedulerUtil.runTask(plugin, () -> {
                            if (!p.isOnline()) {
                                return;
                            }
                            boolean ok = result == CustomEngine.Result.SUCCESS;
                            if (ok) {
                                plugin.screenSound().success(p);
                            } else {
                                plugin.screenSound().error(p);
                            }
                            plugin.messages().send(p, ok ? "custom-created"
                                    : "custom-result-" + result.name().toLowerCase().replace('_', '-'));
                            new CustomScreen(plugin, p, 0).open();
                        })), true);
    }

    private int period() {
        int min = plugin.getConfig().getInt("custom-dynamic.min-period-ticks", 5);
        int max = plugin.getConfig().getInt("custom-dynamic.max-period-ticks", 200);
        return Math.clamp(40, min, Math.max(min, max));
    }

    private ItemStack button(Material material, String... lines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            lore.add(MiniMessage.miniMessage().deserialize(lines[i]).decoration(TextDecoration.ITALIC, false));
        }
        meta.displayName(MiniMessage.miniMessage().deserialize(lines[0]).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String strip(String miniMessage) {
        return miniMessage.replaceAll("<[^>]+>", "");
    }

    private static Preset solid(String name, Material icon, String color) {
        return new Preset("<" + color + ">" + name, icon, false,
                (text, period) -> CustomDraft.staticTag(text, color, icon.name()));
    }

    private static Preset gradient(String name, Material icon, String... colors) {
        return new Preset("<gradient:" + String.join(":", colors) + ">" + name + "</gradient>", icon, true,
                (text, period) -> CustomDraft.gradient(text, List.of(colors), period, icon.name()));
    }

    private static Preset flash(String name, Material icon, String... colors) {
        return new Preset("<gradient:" + String.join(":", colors) + ">" + name + "</gradient>", icon, true,
                (text, period) -> CustomDraft.flash(text, List.of(colors), period, icon.name()));
    }

    private record Preset(String display, Material icon, boolean dynamic,
                          BiFunction<String, Integer, CustomDraft> build) {}

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
