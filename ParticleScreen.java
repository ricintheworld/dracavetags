package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.engine.ParticleEngine;
import com.dracave.tags.handlers.DCTagPart;
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
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class ParticleScreen implements ClickableScreen {
    private static final int SLOTS_PER_PAGE = 45;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREV = 46;
    private static final int SLOT_NEXT = 47;
    private static final int SLOT_MANUAL = 48;
    private static final int SLOT_NONE = 53;
    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");

    private static final String[][] PRESETS = {
            {"DUST", "彩色尘埃", "REDSTONE"},
            {"DUST_COLOR_TRANSITION", "双色渐变尘埃", "GLOWSTONE_DUST"},
            {"FLAME", "火焰", "BLAZE_POWDER"},
            {"SOUL_FIRE_FLAME", "灵魂火", "SOUL_SAND"},
            {"SMALL_FLAME", "小火焰", "FIRE_CHARGE"},
            {"LAVA", "熔岩火花", "LAVA_BUCKET"},
            {"SMOKE", "烟雾", "CAMPFIRE"},
            {"LARGE_SMOKE", "大烟雾", "HAY_BLOCK"},
            {"CAMPFIRE_COSY_SMOKE", "营火烟", "CAMPFIRE"},
            {"CLOUD", "云", "WHITE_WOOL"},
            {"ASH", "灰烬", "BASALT"},
            {"WHITE_ASH", "白灰", "BONE_BLOCK"},
            {"SOUL", "灵魂", "SOUL_LANTERN"},
            {"HEART", "爱心", "POPPY"},
            {"END_ROD", "末地烛", "END_ROD"},
            {"HAPPY_VILLAGER", "绿色星光", "EMERALD"},
            {"ANGRY_VILLAGER", "愤怒村民", "CROSSBOW"},
            {"CRIT", "暴击", "IRON_SWORD"},
            {"DAMAGE_INDICATOR", "伤害指示", "IRON_SWORD"},
            {"ENCHANTED_HIT", "附魔攻击", "DIAMOND_SWORD"},
            {"SWEEP_ATTACK", "横扫", "GOLDEN_SWORD"},
            {"ENCHANT", "附魔符文", "ENCHANTING_TABLE"},
            {"PORTAL", "传送门", "OBSIDIAN"},
            {"REVERSE_PORTAL", "反向传送门", "CRYING_OBSIDIAN"},
            {"SNOWFLAKE", "雪花", "SNOWBALL"},
            {"TOTEM_OF_UNDYING", "不死图腾", "TOTEM_OF_UNDYING"},
            {"ELECTRIC_SPARK", "电火花", "LIGHTNING_ROD"},
            {"CHERRY_LEAVES", "樱花", "CHERRY_LEAVES"},
            {"GLOW", "荧光", "GLOW_INK_SAC"},
            {"GLOW_SQUID_INK", "发光墨汁", "GLOW_INK_SAC"},
            {"SQUID_INK", "墨汁", "INK_SAC"},
            {"NOTE", "音符", "NOTE_BLOCK"},
            {"BUBBLE", "气泡", "WATER_BUCKET"},
            {"BUBBLE_COLUMN_UP", "涌流气泡", "SOUL_SAND"},
            {"SPLASH", "水花", "SALMON"},
            {"FISHING", "钓鱼", "FISHING_ROD"},
            {"DOLPHIN", "海豚", "DOLPHIN_SPAWN_EGG"},
            {"UNDERWATER", "水下颗粒", "KELP"},
            {"DRAGON_BREATH", "龙息", "DRAGON_BREATH"},
            {"FIREWORK", "烟花", "FIREWORK_ROCKET"},
            {"SPELL", "药水", "POTION"},
            {"INSTANT_EFFECT", "喷溅药水", "SPLASH_POTION"},
            {"WITCH", "女巫", "WITCH_SPAWN_EGG"},
            {"ELDER_GUARDIAN", "远古守卫者", "PRISMARINE_SHARD"},
            {"MYCELIUM", "菌丝孢子", "MYCELIUM"},
            {"CRIMSON_SPORE", "绯红孢子", "CRIMSON_NYLIUM"},
            {"WARPED_SPORE", "诡异孢子", "WARPED_NYLIUM"},
            {"SPORE_BLOSSOM_AIR", "孢子花", "SPORE_BLOSSOM"},
            {"COMPOSTER", "堆肥", "COMPOSTER"},
            {"SNEEZE", "熊猫喷嚏", "BAMBOO"},
            {"ITEM_SLIME", "史莱姆", "SLIME_BALL"},
            {"NAUTILUS", "鹦鹉螺", "NAUTILUS_SHELL"},
            {"SCRAPE", "除锈", "IRON_AXE"},
            {"VIBRATION", "振动", "SCULK_SENSOR"},
            {"EGG_CRACK", "蛋裂", "EGG"},
            {"TRIAL_SPAWNER_DETECTION", "试炼检测", "TRIAL_KEY"},
            {"OMINOUS_SPAWNING", "不祥生成", "OMINOUS_TRIAL_KEY"},
    };

    private final DraCaveTags plugin;
    private final Player player;
    private final Consumer<DCTagPart> onPick;
    private final Runnable onCancel;
    private final List<String[]> entries = new ArrayList<>();
    private int page;
    private Inventory inventory;

    public ParticleScreen(DraCaveTags plugin, Player player, Consumer<DCTagPart> onPick, Runnable onCancel) {
        this.plugin = plugin;
        this.player = player;
        this.onPick = onPick;
        this.onCancel = onCancel;
        for (String[] preset : PRESETS) {
            if (ParticleEngine.validParticle(preset[0])) {
                entries.add(preset);
            }
        }
    }

    public void open() {
        int total = (entries.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
        if (page >= total) {
            page = 0;
        }
        inventory = Bukkit.createInventory(this, 54,
                MiniMessage.miniMessage().deserialize("<yellow>选择粒子特效（" + (page + 1) + "/" + Math.max(1, total) + "）</yellow>"));
        renderContent();
        player.openInventory(inventory);
    }

    private void renderContent() {
        int total = Math.max(1, (entries.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
        int start = page * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, entries.size());
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int idx = start + i;
            if (idx < end) {
                String[] entry = entries.get(idx);
                Material material = Material.matchMaterial(entry[2]);
                ItemStack item = new ItemStack(material == null ? Material.FIREWORK_STAR : material);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(MiniMessage.miniMessage().deserialize("<yellow>" + entry[1]).decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(MiniMessage.miniMessage().deserialize("<dark_gray>" + entry[0]).decoration(TextDecoration.ITALIC, false)));
                item.setItemMeta(meta);
                inventory.setItem(i, item);
            } else {
                inventory.setItem(i, null);
            }
        }
        inventory.setItem(SLOT_BACK, plain(Material.ARROW, "<yellow>返回"));
        if (page > 0) {
            inventory.setItem(SLOT_PREV, plain(Material.PAPER, "<yellow>上一页"));
        } else {
            inventory.setItem(SLOT_PREV, null);
        }
        if (page < total - 1) {
            inventory.setItem(SLOT_NEXT, plain(Material.PAPER, "<yellow>下一页"));
        } else {
            inventory.setItem(SLOT_NEXT, null);
        }
        inventory.setItem(SLOT_MANUAL, plain(Material.WRITABLE_BOOK, "<yellow>手动输入"));
        inventory.setItem(SLOT_NONE, plain(Material.BARRIER, "<red>移除粒子特效"));
    }

    private ItemStack plain(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(name).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void click(int rawSlot, ClickType clickType) {
        if (rawSlot == SLOT_BACK) {
            onCancel.run();
        } else if (rawSlot == SLOT_PREV && page > 0) {
            page--;
            int total = (entries.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
            inventory = Bukkit.createInventory(this, 54,
                    MiniMessage.miniMessage().deserialize("<yellow>选择粒子特效（" + (page + 1) + "/" + Math.max(1, total) + "）</yellow>"));
            renderContent();
            player.openInventory(inventory);
        } else if (rawSlot == SLOT_NEXT) {
            int total = (entries.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
            if (page < total - 1) {
                page++;
                inventory = Bukkit.createInventory(this, 54,
                        MiniMessage.miniMessage().deserialize("<yellow>选择粒子特效（" + (page + 1) + "/" + Math.max(1, total) + "）</yellow>"));
                renderContent();
                player.openInventory(inventory);
            }
        } else if (rawSlot == SLOT_MANUAL) {
            promptManual();
        } else if (rawSlot == SLOT_NONE) {
            onPick.accept(null);
        } else if (rawSlot >= 0 && rawSlot < SLOTS_PER_PAGE) {
            int idx = page * SLOTS_PER_PAGE + rawSlot;
            if (idx >= entries.size()) {
                return;
            }
            String type = entries.get(idx)[0];
            switch (type) {
                case "DUST" -> pickColors(type, 1, new ArrayList<>());
                case "DUST_COLOR_TRANSITION" -> pickColors(type, 2, new ArrayList<>());
                default -> onPick.accept(new DCTagPart(type, null, List.of()));
            }
        }
    }

    private void promptManual() {
        player.closeInventory();
        plugin.chatPrompt().prompt(player, "§e请输入粒子（如 DUST #FF0000，最多 3 个颜色）：", (p, value) -> {
            String[] parts = value.trim().split("\\s+");
            String type = parts[0].toUpperCase();
            if (!ParticleEngine.validParticle(type)) {
                p.sendMessage("§c未知粒子类型：" + parts[0]);
                onCancel.run();
                return;
            }
            List<String> colors = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                if (!HEX.matcher(parts[i]).matches()) {
                    p.sendMessage("§c颜色必须是 #RRGGBB。");
                    onCancel.run();
                    return;
                }
                colors.add(parts[i].toUpperCase());
            }
            if (colors.size() > 3) {
                p.sendMessage("§c最多 3 个颜色。");
                onCancel.run();
                return;
            }
            onPick.accept(new DCTagPart(type, null, colors));
        }, true);
    }

    private void pickColors(String type, int needed, List<String> picked) {
        if (picked.size() >= needed) {
            onPick.accept(new DCTagPart(type, null, List.copyOf(picked)));
            return;
        }
        player.sendMessage("§e请选择第 " + (picked.size() + 1) + " / " + needed + " 个颜色。");
        new ColorScreen(plugin, player, color -> {
            picked.add(color);
            pickColors(type, needed, picked);
        }, onCancel).open();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
