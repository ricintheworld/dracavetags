package com.dracave.tags.screen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.config.Cfg;
import com.dracave.tags.engine.CustomEngine;
import com.dracave.tags.economy.EcoProvider;
import com.dracave.tags.handlers.CustomDCTag;
import com.dracave.tags.handlers.CustomDraft;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.DCTagAnim;
import com.dracave.tags.handlers.DCTagType;
import com.dracave.tags.handlers.EcoType;
import com.dracave.tags.render.DCTagRenderer;
import com.dracave.tags.util.ItemResolver;
import com.dracave.tags.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 玩家自定义称号创建/编辑面板（精简版管理员面板）。
 * 只包含：动画实时预览、颜色自定义、称号文本、图标、颜色周期、动画类型、渐变方向、描述。
 */
public final class CustomCreateScreen implements ClickableScreen {
    private static final int DEFAULT_PERIOD_TICKS = 40;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final List<String> ICON_PRESETS = List.of(
            "NAME_TAG", "PAPER", "BOOK", "WRITABLE_BOOK", "NETHER_STAR", "DIAMOND", "EMERALD", "GOLD_INGOT", "IRON_INGOT",
            "REDSTONE", "LAPIS_LAZULI", "AMETHYST_SHARD", "ENDER_EYE", "ENDER_PEARL", "BLAZE_POWDER", "GHAST_TEAR",
            "TOTEM_OF_UNDYING", "HEART_OF_THE_SEA", "DRAGON_EGG", "ELYTRA", "TRIDENT", "NETHERITE_SWORD", "BOW", "SHIELD",
            "ENCHANTED_GOLDEN_APPLE", "SUNFLOWER", "BEACON", "SKELETON_SKULL", "DRAGON_HEAD", "CAKE", "FIREWORK_STAR",
            "GLOW_INK_SAC", "SPYGLASS", "RECOVERY_COMPASS", "ECHO_SHARD", "CHERRY_LEAVES");

    private static final List<DCTagType> TYPE_CYCLE = Arrays.asList(
            DCTagType.STATIC, DCTagType.RAINBOW, DCTagType.FLOWING_GRADIENT, DCTagType.FLASHING_COLORS);

    private final DraCaveTags plugin;
    private final Player player;
    private final CustomDCTag editTarget; // null = 创建模式，非 null = 编辑模式
    private Inventory inventory;

    // 草稿字段
    private String display = "";
    private String icon = "NAME_TAG";
    private DCTagType animType = DCTagType.STATIC;
    private final List<String> colors = new ArrayList<>();
    private int periodTicks = DEFAULT_PERIOD_TICKS;
    private DCTagAnim.GradientStyle gradientStyle = DCTagAnim.GradientStyle.CYCLE;
    private final List<String> description = new ArrayList<>();

    private SchedulerUtil.Task refreshTask;

    /** 创建模式 */
    public CustomCreateScreen(DraCaveTags plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.editTarget = null;
    }

    /** 编辑模式 */
    public CustomCreateScreen(DraCaveTags plugin, Player player, CustomDCTag editTarget) {
        this.plugin = plugin;
        this.player = player;
        this.editTarget = editTarget;
        // 预填已有数据
        this.display = editTarget.text();
        this.icon = editTarget.icon();
        this.animType = editTarget.type();
        this.colors.addAll(editTarget.colors());
        this.periodTicks = editTarget.periodTicks();
        DCTag rendered = plugin.registry().get(editTarget.id());
        if (rendered != null) {
            if (rendered.animation() != null) {
                this.gradientStyle = rendered.animation().style();
            }
            List<String> desc = rendered.description();
            if (desc != null && !desc.isEmpty()) {
                String first = desc.get(0);
                // 过滤掉系统生成的默认描述
                if (!"<gray>玩家自定义称号</gray>".equals(first) && !"<gray>Player custom tag</gray>".equals(first)) {
                    this.description.add(first);
                }
            }
        }
    }

    public void open() {
        boolean isEdit = editTarget != null;
        String title = isEdit ? "<gradient:#55FFFF:#FF55FF>编辑自定义称号</gradient>"
                : "<gradient:#55FFFF:#FF55FF>创建自定义称号</gradient>";
        inventory = Bukkit.createInventory(this, 54, MINI.deserialize(title));

        // 返回按钮
        inventory.setItem(0, button(Material.ARROW, "<yellow>返回"));

        // 预览区
        inventory.setItem(4, previewItem());

        // 称号文本
        inventory.setItem(9, button(Material.NAME_TAG, "<yellow>称号文本\n<gray>当前：<white>" + (display.isEmpty() ? "未设置" : display) + "\n<dark_gray>点击修改"));

        // 图标
        inventory.setItem(10, button(Material.ITEM_FRAME, "<yellow>图标\n<gray>当前：<white>" + icon + "\n<dark_gray>点击选择"));

        // 颜色周期
        inventory.setItem(12, button(Material.CLOCK, "<yellow>动画周期\n<gray>当前：<white>" + periodDisplay() + "\n<dark_gray>点击修改（秒）"));

        // 动画类型
        inventory.setItem(13, button(Material.BLAZE_ROD, "<yellow>动画类型\n<gray>当前：<white>" + typeDisplay()
                + "\n<dark_gray>点击切换（渐变/闪烁需要至少 2 个颜色）"));

        // 渐变方向
        inventory.setItem(14, button(Material.COMPASS, "<yellow>渐变方向\n<gray>当前：<white>" + modeDisplay()
                + "\n<dark_gray>点击在循环/回弹之间切换"));

        // 描述
        inventory.setItem(15, button(Material.MAP, "<yellow>描述\n<gray>当前：<white>" + (description.isEmpty() ? "无" : description.get(0)) + "\n<dark_gray>点击修改"));

        // 添加颜色按钮
        inventory.setItem(18, button(Material.LIME_DYE, "<green>添加颜色\n<dark_gray>点击打开调色板"));

        // 颜色槽
        for (int i = 0; i < 8; i++) {
            int slot = 19 + i;
            if (i < colors.size()) {
                inventory.setItem(slot, colored(colors.get(i), i));
            } else {
                inventory.setItem(slot, button(Material.GRAY_STAINED_GLASS_PANE, "<gray>空颜色槽 " + (i + 1)));
            }
        }

        // 提交按钮
        String btnText = isEdit ? "<green>保存修改\n<dark_gray>点击更新自定义称号"
                : "<green>创建称号\n<dark_gray>点击创建自定义称号";
        inventory.setItem(45, button(Material.LIME_CONCRETE, btnText));

        player.openInventory(inventory);
        refreshTask = SchedulerUtil.runTaskTimer(plugin, this::refreshPreview, 2L, 2L);
    }

    private ItemStack previewItem() {
        DCTag preview = buildPreview();
        ItemStack item = ItemResolver.resolve(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(DCTagRenderer.component(preview, System.currentTimeMillis())
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    MINI.deserialize("<gray>动画实时预览"),
                    MINI.deserialize("<gray>文本: " + (display.isEmpty() ? "未设置" : display)),
                    MINI.deserialize("<gray>类型: " + typeDisplay()),
                    MINI.deserialize("<gray>颜色数: " + colors.size()),
                    MINI.deserialize("<gray>描述: " + (description.isEmpty() ? "无" : description.get(0)))
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private DCTag buildPreview() {
        DCTagAnim animation = buildAnimation();
        String displayText = display.isEmpty() ? "预览" : display;
        String miniText = animType == DCTagType.STATIC && !colors.isEmpty()
                ? "<" + colors.get(0) + ">" + MINI.escapeTags(displayText) + "</" + colors.get(0) + ">"
                : MINI.escapeTags(displayText);
        return new DCTag("_preview_", miniText, description, icon, 0, false, "",
                animation, null, List.of(), false, List.of(), null, 0);
    }

    private DCTagAnim buildAnimation() {
        if (animType == DCTagType.STATIC) return null;
        if (animType == DCTagType.RAINBOW) return DCTagAnim.rainbow(periodTicks);
        DCTagAnim.AnimType type = switch (animType) {
            case FLOWING_GRADIENT -> DCTagAnim.AnimType.FLOWING_GRADIENT;
            case FLASHING_COLORS -> DCTagAnim.AnimType.FLASHING_COLORS;
            default -> DCTagAnim.AnimType.FLOWING_GRADIENT;
        };
        List<String> c = colors.size() >= 2 ? colors : List.of("#55FFFF", "#FF55FF");
        return new DCTagAnim(type, c, List.of(), periodTicks, gradientStyle);
    }

    private ItemStack colored(String hex, int index) {
        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();
        try {
            ((LeatherArmorMeta) meta).setColor(Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16)));
        } catch (NumberFormatException ignored) {
        }
        meta.displayName(MINI.deserialize("<" + hex + ">颜色 " + (index + 1) + " (" + hex + ")")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MINI.deserialize("<dark_gray>左键移除"),
                MINI.deserialize("<dark_gray>右键右移 / Shift+左键左移")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        List<Component> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            lines.add(MINI.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.displayName(lines.get(0));
        if (lines.size() > 1) meta.lore(lines.subList(1, lines.size()));
        item.setItemMeta(meta);
        return item;
    }

    private String periodDisplay() {
        if (animType == DCTagType.STATIC) return "无动画";
        return String.format("%.1f 秒", periodTicks / 20.0);
    }

    private String typeDisplay() {
        return switch (animType) {
            case STATIC -> "静态";
            case FLOWING_GRADIENT -> "流动渐变";
            case RAINBOW -> "彩虹";
            case FLASHING_COLORS -> "颜色闪烁";
            default -> "未知";
        };
    }

    private String modeDisplay() {
        if (animType == DCTagType.STATIC || animType == DCTagType.RAINBOW) return "-";
        return gradientStyle == DCTagAnim.GradientStyle.PINGPONG ? "回弹" : "循环";
    }

    private void refreshPreview() {
        if (inventory == null) return;
        ItemStack preview = previewItem();
        inventory.setItem(4, preview);
    }

    @Override
    public void click(int rawSlot, ClickType clickType) {
        switch (rawSlot) {
            case 0 -> {
                close();
                new CustomScreen(plugin, player, 0).open();
            }
            case 9 -> {
                // 称号文本
                prompt("§e请输入称号文本（纯文本，1-16 字符）：", (p, value) -> {
                    String text = MINI.escapeTags(value.trim());
                    if (text.isEmpty() || text.codePointCount(0, text.length()) > 16) {
                        p.sendMessage("§c文本长度必须为 1 至 16 个字符。");
                        open();
                        return;
                    }
                    display = text;
                    open();
                });
            }
            case 10 -> openIconPicker();
            case 12 -> {
                // 颜色周期
                if (animType == DCTagType.STATIC) {
                    player.sendMessage("§c当前为静态称号，无需设置动画周期。请先切换动画类型。");
                    return;
                }
                prompt("§e请输入动画周期（秒，0.2-10）：", (p, value) -> {
                    try {
                        double seconds = Double.parseDouble(value.trim());
                        if (seconds < 0.2 || seconds > 10.0) {
                            p.sendMessage("§c周期必须在 0.2 至 10 秒之间。");
                            open();
                            return;
                        }
                        periodTicks = (int) Math.round(seconds * 20.0);
                    } catch (NumberFormatException ex) {
                        p.sendMessage("§c请输入合法数字。");
                        open();
                        return;
                    }
                    open();
                });
            }
            case 13 -> {
                // 动画类型循环
                cycleType();
                open();
            }
            case 14 -> {
                // 渐变方向
                if (animType == DCTagType.STATIC || animType == DCTagType.RAINBOW) {
                    player.sendMessage("§c当前动画类型无渐变方向设置。");
                    return;
                }
                gradientStyle = gradientStyle == DCTagAnim.GradientStyle.PINGPONG
                        ? DCTagAnim.GradientStyle.CYCLE : DCTagAnim.GradientStyle.PINGPONG;
                open();
            }
            case 15 -> {
                // 描述
                prompt("§e请输入称号描述（一行，none 清除）：", (p, value) -> {
                    if (value.equalsIgnoreCase("none")) {
                        description.clear();
                    } else {
                        String desc = value.trim();
                        if (description.isEmpty()) {
                            description.add(desc);
                        } else {
                            description.set(0, desc);
                        }
                    }
                    open();
                });
            }
            case 18 -> {
                // 添加颜色
                if (colors.size() >= 8) {
                    player.sendMessage("§c最多 8 个颜色。");
                    return;
                }
                new ColorScreen(plugin, player, color -> {
                    colors.add(color);
                    open();
                }, this::open).open();
            }
            case 45 -> {
                // 提交
                if (display.isEmpty()) {
                    player.sendMessage("§c请先设置称号文本。");
                    return;
                }
                submit();
            }
            default -> {
                // 颜色槽操作
                if (rawSlot >= 19 && rawSlot <= 26) {
                    int index = rawSlot - 19;
                    if (index < colors.size()) {
                        if (clickType == ClickType.RIGHT) {
                            moveColor(index, 1);
                        } else if (clickType == ClickType.SHIFT_LEFT) {
                            moveColor(index, -1);
                        } else {
                            colors.remove(index);
                        }
                        open();
                    }
                }
            }
        }
    }

    private void cycleType() {
        int idx = TYPE_CYCLE.indexOf(animType);
        DCTagType next = TYPE_CYCLE.get((idx + 1) % TYPE_CYCLE.size());
        if (next != DCTagType.STATIC && next != DCTagType.RAINBOW && colors.size() < 2) {
            player.sendMessage("§7颜色不足 2 个，无法切换到需要颜色的动画类型。请先添加颜色。");
            return;
        }
        animType = next;
    }

    private void moveColor(int index, int direction) {
        int target = index + direction;
        if (target < 0 || target >= colors.size()) return;
        String color = colors.remove(index);
        colors.add(target, color);
    }

    private void openIconPicker() {
        List<PresetScreen.Option<Material>> options = new ArrayList<>();
        for (String name : ICON_PRESETS) {
            Material material = Material.matchMaterial(name);
            if (material != null && !material.isAir()) {
                options.add(new PresetScreen.Option<>(material, "<yellow>" + material.name(), null, material));
            }
        }
        new PresetScreen<>(player, "<yellow>选择图标</yellow>", options, material -> {
            icon = material.name();
            open();
        }, this::open, this::promptIcon).open();
    }

    private void promptIcon() {
        prompt("§e请输入物品材质名称（如 NAME_TAG、head:纹理 或 base64:序列化，none 恢复默认）：", (p, value) -> {
            if (value.equalsIgnoreCase("none")) {
                icon = "NAME_TAG";
            } else {
                if (!ItemResolver.isValid(value)) {
                    p.sendMessage("§c无效的物品材质。");
                    open();
                    return;
                }
                icon = value;
            }
            open();
        });
    }

    private void submit() {
        player.closeInventory();
        if (editTarget != null) {
            update();
        } else {
            create();
        }
    }

    private void update() {
        CustomDraft draft = buildDraft();
        plugin.customEngine().update(player, editTarget.id(), draft).thenAccept(result ->
                SchedulerUtil.runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (result == CustomEngine.Result.SUCCESS) {
                        plugin.screenSound().success(player);
                        plugin.messages().send(player, "custom-updated");
                    } else {
                        plugin.screenSound().error(player);
                        plugin.messages().send(player, "custom-result-" + result.name().toLowerCase(Locale.ROOT).replace('_', '-'));
                    }
                    new CustomScreen(plugin, player, 0).open();
                }));
    }

    private void create() {
        // 检查创建花费
        boolean costEnabled = plugin.getConfig().getBoolean(Cfg.CUSTOM_CREATION_COST_ENABLED, false);
        BigDecimal costAmount = BigDecimal.ZERO;
        EcoType costType = null;
        EcoProvider costProvider = null;
        if (costEnabled) {
            try {
                costAmount = new BigDecimal(plugin.getConfig().getString(Cfg.CUSTOM_CREATION_COST_AMOUNT, "0"));
            } catch (NumberFormatException ignored) {
                costAmount = BigDecimal.ZERO;
            }
            if (costAmount.compareTo(BigDecimal.ZERO) > 0) {
                String typeStr = plugin.getConfig().getString(Cfg.CUSTOM_CREATION_COST_TYPE, "vault");
                costType = EcoType.parse(typeStr);
                costProvider = plugin.currencies() != null ? plugin.currencies().get(costType) : null;
                if (costProvider == null || !costProvider.available()) {
                    player.sendMessage("§c货币服务不可用，无法创建称号。");
                    new CustomScreen(plugin, player, 0).open();
                    return;
                }
                BigDecimal balance = costProvider.balance(player.getUniqueId());
                if (balance == null || balance.compareTo(costAmount) < 0) {
                    String currencyName = plugin.getConfig().getString("shop.currencies." + costType.id() + ".display", costType.id());
                    player.sendMessage("§c创建自定义称号需要 " + costAmount.toPlainString() + " " + currencyName + "，你的余额不足。");
                    new CustomScreen(plugin, player, 0).open();
                    return;
                }
                boolean withdrawn = costProvider.withdraw(player.getUniqueId(), costAmount);
                if (!withdrawn) {
                    player.sendMessage("§c扣费失败，请稍后重试。");
                    new CustomScreen(plugin, player, 0).open();
                    return;
                }
            } else {
                costEnabled = false;
            }
        }
        final boolean didCharge = costEnabled && costAmount.compareTo(BigDecimal.ZERO) > 0;
        final BigDecimal chargedAmount = costAmount;
        final EcoProvider chargedProvider = costProvider;

        CustomDraft draft = buildDraft();
        plugin.customEngine().create(player, draft).thenAccept(result ->
                SchedulerUtil.runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        if (didCharge && chargedProvider != null) {
                            chargedProvider.refund(player.getUniqueId(), chargedAmount);
                        }
                        return;
                    }
                    if (result == CustomEngine.Result.SUCCESS) {
                        plugin.screenSound().success(player);
                        plugin.messages().send(player, "custom-created");
                    } else {
                        if (didCharge && chargedProvider != null) {
                            chargedProvider.refund(player.getUniqueId(), chargedAmount);
                            plugin.messages().send(player, "custom-creation-cost-refunded");
                        }
                        plugin.screenSound().error(player);
                        plugin.messages().send(player, "custom-result-" + result.name().toLowerCase(Locale.ROOT).replace('_', '-'));
                    }
                    new CustomScreen(plugin, player, 0).open();
                }));
    }

    private CustomDraft buildDraft() {
        return switch (animType) {
            case STATIC -> CustomDraft.staticTag(display, colors.isEmpty() ? "#55FFFF" : colors.get(0), icon);
            case FLOWING_GRADIENT -> CustomDraft.gradient(display, colors.size() >= 2 ? colors : List.of("#55FFFF", "#FF55FF"), periodTicks, icon);
            case RAINBOW -> CustomDraft.rainbow(display, periodTicks, icon);
            case FLASHING_COLORS -> CustomDraft.flash(display, colors.size() >= 2 ? colors : List.of("#55FFFF", "#FF55FF"), periodTicks, icon);
            case TEXT_FRAMES -> CustomDraft.staticTag(display, colors.isEmpty() ? "#55FFFF" : colors.get(0), icon);
        };
    }

    private void prompt(String message, java.util.function.BiConsumer<Player, String> handler) {
        player.closeInventory();
        plugin.chatPrompt().prompt(player, message, (p, value) -> {
            handler.accept(p, value);
        }, true);
    }

    private void close() {
        if (refreshTask != null) refreshTask.cancel();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void onClose() {
        close();
    }
}