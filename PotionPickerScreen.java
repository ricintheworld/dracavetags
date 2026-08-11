package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.util.SchedulerUtil;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public final class PotionPickerScreen implements ClickableScreen {
    private static final String[][] POTIONS = {
            {"SPEED", "速度", "SUGAR"},
            {"SLOWNESS", "缓慢", "SOUL_SAND"},
            {"HASTE", "急迫", "GOLDEN_PICKAXE"},
            {"MINING_FATIGUE", "挖掘疲劳", "WOODEN_PICKAXE"},
            {"STRENGTH", "力量", "BLAZE_POWDER"},
            {"JUMP_BOOST", "跳跃提升", "RABBIT_FOOT"},
            {"NAUSEA", "反胃", "POISONOUS_POTATO"},
            {"REGENERATION", "生命恢复", "GHAST_TEAR"},
            {"RESISTANCE", "抗性提升", "IRON_CHESTPLATE"},
            {"FIRE_RESISTANCE", "防火", "MAGMA_CREAM"},
            {"WATER_BREATHING", "水下呼吸", "TURTLE_HELMET"},
            {"INVISIBILITY", "隐身", "FERMENTED_SPIDER_EYE"},
            {"BLINDNESS", "失明", "INK_SAC"},
            {"NIGHT_VISION", "夜视", "GOLDEN_CARROT"},
            {"HUNGER", "饥饿", "ROTTEN_FLESH"},
            {"WEAKNESS", "虚弱", "WOODEN_SWORD"},
            {"POISON", "中毒", "SPIDER_EYE"},
            {"WITHER", "凋零", "WITHER_SKELETON_SKULL"},
            {"HEALTH_BOOST", "生命提升", "GOLDEN_APPLE"},
            {"ABSORPTION", "伤害吸收", "GOLDEN_APPLE"},
            {"SATURATION", "饱和", "COOKED_BEEF"},
            {"GLOWING", "发光", "GLOWSTONE_DUST"},
            {"LEVITATION", "漂浮", "SHULKER_SHELL"},
            {"LUCK", "幸运", "RABBIT_FOOT"},
            {"UNLUCK", "霉运", "DEAD_BUSH"},
            {"SLOW_FALLING", "缓降", "PHANTOM_MEMBRANE"},
            {"CONDUIT_POWER", "潮涌能量", "HEART_OF_THE_SEA"},
            {"DOLPHINS_GRACE", "海豚的恩惠", "COD"},
            {"BAD_OMEN", "不祥之兆", "OMINOUS_BOTTLE"},
            {"HERO_OF_THE_VILLAGE", "村庄英雄", "EMERALD"},
            {"DARKNESS", "黑暗", "SCULK_CATALYST"},
    };

    private static final int SLOTS_PER_PAGE = 45;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREV = 46;
    private static final int SLOT_NEXT = 47;
    private static final int SLOT_CLEAR = 53;

    private final DraCaveTags plugin;
    private final Player player;
    private final Map<String, Integer> selected;
    private final BiConsumer<Player, Map<String, Integer>> onDone;
    private final Runnable onCancel;
    private final List<String[]> available = new ArrayList<>();
    private int page;
    private Inventory inventory;

    public PotionPickerScreen(DraCaveTags plugin, Player player, Map<String, Integer> selected,
                              BiConsumer<Player, Map<String, Integer>> onDone, Runnable onCancel) {
        this.plugin = plugin;
        this.player = player;
        this.selected = new LinkedHashMap<>(selected);
        this.onDone = onDone;
        this.onCancel = onCancel;
        for (String[] potion : POTIONS) {
            if (PotionEffectType.getByName(potion[0]) != null) {
                available.add(potion);
            }
        }
    }

    public void open() {
        int totalPages = (available.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
        if (page >= totalPages) {
            page = 0;
        }
        inventory = Bukkit.createInventory(this, 54,
                MiniMessage.miniMessage().deserialize("<yellow>药水效果（多选）</yellow>"));
        renderContent();
        player.openInventory(inventory);
    }

    private void renderContent() {
        int totalPages = Math.max(1, (available.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
        int start = page * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, available.size());
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int idx = start + i;
            if (idx < end) {
                inventory.setItem(i, potionItem(idx));
            } else {
                inventory.setItem(i, null);
            }
        }
        inventory.setItem(SLOT_BACK, button(Material.ARROW, "<yellow>返回"));
        if (page > 0) {
            inventory.setItem(SLOT_PREV, button(Material.PAPER, "<yellow>上一页"));
        } else {
            inventory.setItem(SLOT_PREV, null);
        }
        if (page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, button(Material.PAPER, "<yellow>下一页"));
        } else {
            inventory.setItem(SLOT_NEXT, null);
        }
        inventory.setItem(SLOT_CLEAR, button(Material.BARRIER,
                "<red>清除全部 (" + selected.size() + " 个已选)"));
    }

    private ItemStack potionItem(int idx) {
        String[] entry = available.get(idx);
        String typeName = entry[0];
        int level = selected.getOrDefault(typeName, 1);
        boolean picked = selected.containsKey(typeName);
        ItemStack item = new ItemStack(picked ? Material.POTION : Material.GLASS_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        String color = picked ? "<green>" : "<gray>";
        meta.displayName(MiniMessage.miniMessage().deserialize(color + entry[1] + " " + toRoman(level))
                .decoration(TextDecoration.ITALIC, false));
        List<String> loreList = new ArrayList<>();
        loreList.add("<dark_gray>" + typeName);
        if (picked) {
            loreList.add("<gray>左键：降低等级");
            loreList.add("<gray>右键：提升等级");
            loreList.add("<gray>Shift+左键：移除");
            if (level >= 1) {
                loreList.add("<gold>当前等级：" + level);
            }
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            loreList.add("<yellow>点击选择");
        }
        meta.lore(loreList.stream().map(l -> MiniMessage.miniMessage().deserialize(l)
                .decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        for (String line : text.split("\n")) {
            if (meta.displayName() == null) {
                meta.displayName(MiniMessage.miniMessage().deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void click(int rawSlot, ClickType clickType) {
        if (rawSlot == SLOT_BACK) {
            onCancel.run();
        } else if (rawSlot == SLOT_PREV && page > 0) {
            page--;
            renderContent();
        } else if (rawSlot == SLOT_NEXT) {
            int totalPages = (available.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
            if (page < totalPages - 1) {
                page++;
                renderContent();
            }
        } else if (rawSlot == SLOT_CLEAR) {
            selected.clear();
            renderContent();
        } else if (rawSlot >= 0 && rawSlot < SLOTS_PER_PAGE) {
            int idx = page * SLOTS_PER_PAGE + rawSlot;
            if (idx >= available.size()) {
                return;
            }
            String typeName = available.get(idx)[0];
            if (selected.containsKey(typeName)) {
                if (clickType == ClickType.SHIFT_LEFT) {
                    selected.remove(typeName);
                } else if (clickType == ClickType.RIGHT) {
                    int newLevel = Math.min(255, selected.get(typeName) + 1);
                    selected.put(typeName, newLevel);
                } else {
                    int newLevel = Math.max(1, selected.get(typeName) - 1);
                    selected.put(typeName, newLevel);
                }
            } else {
                selected.put(typeName, 1);
            }
            renderContent();
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void onClose() {
        SchedulerUtil.runTask(plugin, () -> onDone.accept(player, new LinkedHashMap<>(selected)));
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}
