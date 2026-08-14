package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.api.DCTagResult;
import com.dracave.tags.util.SchedulerUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class GrantDurationScreen implements ClickableScreen {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final DraCaveTags plugin; private final Player admin; private final Player target; private final String tagId; private final PlayerTagScreen back;
    private Inventory inventory;
    public GrantDurationScreen(DraCaveTags plugin, Player admin, Player target, String tagId, PlayerTagScreen back) { this.plugin=plugin; this.admin=admin; this.target=target; this.tagId=tagId; this.back=back; }
    public void open() {
        inventory = Bukkit.createInventory(this, 27, MINI.deserialize("<red>设置发放天数"));
        inventory.setItem(10, item(Material.NETHER_STAR, "<green>永久"));
        inventory.setItem(12, item(Material.CLOCK, "<yellow>1 天"));
        inventory.setItem(14, item(Material.CLOCK, "<yellow>7 天"));
        inventory.setItem(16, item(Material.CLOCK, "<yellow>30 天"));
        inventory.setItem(22, item(Material.PAPER, "<aqua>自定义天数"));
        inventory.setItem(26, item(Material.BARRIER, "<red>返回"));
        plugin.screenSound().open(admin); admin.openInventory(inventory);
    }
    private ItemStack item(Material mat, String display) { ItemStack i=new ItemStack(mat); var m=i.getItemMeta(); if(m!=null){m.displayName(MINI.deserialize(display)); i.setItemMeta(m);} return i; }
    @Override public void click(int slot, ClickType clickType) {
        if (slot == 26) { back.open(); return; }
        if (slot == 22) {
            admin.closeInventory();
            plugin.chatPrompt().prompt(admin, "请输入发放天数（0 表示永久）：", (p, input) -> { try { int days=Integer.parseInt(input); if(days<0) throw new NumberFormatException(); grant(days); } catch(NumberFormatException e){ p.sendMessage("§c请输入非负整数"); } }, true);
            return;
        }
        int days = switch (slot) { case 10 -> 0; case 12 -> 1; case 14 -> 7; case 16 -> 30; default -> -1; };
        if (days >= 0) grant(days);
    }
    private void grant(int days) {
        admin.closeInventory();
        plugin.tagEngine().grant(target.getUniqueId(), tagId, days, true).thenCompose(r -> r == DCTagResult.SUCCESS ? plugin.tagEngine().equip(target.getUniqueId(), tagId, true) : java.util.concurrent.CompletableFuture.completedFuture(r)).thenAccept(result -> SchedulerUtil.runTask(plugin, () -> {
            if (result == DCTagResult.SUCCESS || result == DCTagResult.ALREADY_EQUIPPED) {
                admin.sendMessage("§a已为 " + target.getName() + " 发放并穿戴称号 " + tagId + "（" + (days == 0 ? "永久" : days + " 天") + "）");
                if (target.isOnline()) target.sendMessage("§a管理员已为你发放并穿戴称号：" + tagId);
            } else admin.sendMessage("§c发放失败：" + result);
            new PlayerActionScreen(plugin, admin, target).open();
        }));
    }
    @Override public Inventory getInventory() { return inventory; }
}