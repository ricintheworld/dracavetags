package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.config.GuiConfig.*;
import com.dracave.tags.config.GuiConfig;
import com.dracave.tags.handlers.RewardCfg;
import com.dracave.tags.util.SchedulerUtil;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public final class RewardScreen implements ClickableScreen {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final DraCaveTags plugin;
    private final Player player;
    private final Map<Integer, Long> rewardSlots = new HashMap<>();
    private final Map<Integer, IconAction> actions = new HashMap<>();
    private final MenuDef menu;
    private Inventory inventory;

    public RewardScreen(DraCaveTags plugin, Player player) {
        this.plugin = plugin; this.player = player;
        this.menu = plugin.guiConfig() != null ? plugin.guiConfig().get("reward") : null;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, menu != null ? menu.size() : 45, MINI.deserialize(menu != null ? menu.title() : ""));
        rewardSlots.clear(); actions.clear();
        int unlockedCount = 0;
        var data = plugin.tagEngine().getCached(player.getUniqueId());
        if (data != null) unlockedCount = data.unlocked().size();
        List<RewardCfg> rewards = plugin.rewardEngine().all();
        if (menu != null) {
            for (Map.Entry<Integer, IconDef> e : menu.slots().entrySet()) {
                if (menu.contentSlots().contains(e.getKey())) continue;
                IconDef icon = e.getValue();
                if (icon.permission() != null && !player.hasPermission(icon.permission())) continue;
                inventory.setItem(e.getKey(), plugin.guiConfig().buildItem(icon, Map.of()));
                if (icon.left() != IconAction.NONE) actions.put(e.getKey(), icon.left());
            }
        }
        int slot = 0;
        for (RewardCfg reward : rewards) {
            if (slot >= 36) break;
            rewardSlots.put(slot, reward.id());
            boolean claimed = plugin.rewardEngine().isClaimed(player.getUniqueId(), reward.id());
            boolean met = unlockedCount >= reward.number();
            String title = reward.kind().id();
            String d = met && !claimed ? "<green>" + reward.amount() + " " + title : "<gray>" + reward.amount() + " " + title;
            inventory.setItem(slot, rewardItem(d, claimed, met));
            slot++;
        }
        plugin.screenSound().open(player);
        player.openInventory(inventory);
    }

    private ItemStack rewardItem(String display, boolean claimed, boolean met) {
        Material mat = claimed ? Material.GRAY_STAINED_GLASS_PANE : met ? Material.GOLD_INGOT : Material.BARRIER;
        ItemStack i = new ItemStack(mat);
        var m = i.getItemMeta(); if (m == null) return i;
        m.displayName(MINI.deserialize(display).decoration(TextDecoration.ITALIC, false));
        if (claimed) m.lore(java.util.List.of(MINI.deserialize("<gray>已领取")));
        else if (met) m.lore(java.util.List.of(MINI.deserialize("<green>点击领取!")));
        else m.lore(java.util.List.of(MINI.deserialize("<red>未满足条件")));
        i.setItemMeta(m); return i;
    }

    @Override public void click(int rawSlot, ClickType clickType) {
        plugin.screenSound().click(player);
        IconAction act = actions.getOrDefault(rawSlot, IconAction.NONE);
        if (act == IconAction.OPEN_MAIN_MENU) { new MainScreen(plugin, player).open(); return; }
        Long rid = rewardSlots.get(rawSlot);
        if (rid != null) {
            SchedulerUtil.runTaskAsynchronously(plugin, () -> {
                try { plugin.rewardEngine().claim(player, rid); }
                catch (Exception ignored) {}
                SchedulerUtil.runTask(plugin, () -> player.closeInventory());
            });
        }
    }

    @Override public Inventory getInventory() { return inventory; }
}
