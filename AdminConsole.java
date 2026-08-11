package com.dracave.tags.panel;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.config.Locale;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.DCTagAnim;
import com.dracave.tags.handlers.DCTagOffer;
import com.dracave.tags.handlers.DCTagPart;
import com.dracave.tags.handlers.DCTagPotion;
import com.dracave.tags.handlers.EcoType;
import com.dracave.tags.render.DCTagRenderer;
import com.dracave.tags.screen.AdminScreen;
import com.dracave.tags.screen.ClickableScreen;
import com.dracave.tags.screen.ColorScreen;
import com.dracave.tags.screen.ParticleScreen;
import com.dracave.tags.screen.PotionPickerScreen;
import com.dracave.tags.screen.PresetScreen;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminConsole implements AutoCloseable {
    private static final int DEFAULT_PERIOD_TICKS = 40;

    private static final List<String> ICON_PRESETS = List.of(
            "NAME_TAG", "PAPER", "BOOK", "WRITABLE_BOOK", "NETHER_STAR", "DIAMOND", "EMERALD", "GOLD_INGOT", "IRON_INGOT",
            "REDSTONE", "LAPIS_LAZULI", "AMETHYST_SHARD", "ENDER_EYE", "ENDER_PEARL", "BLAZE_POWDER", "GHAST_TEAR",
            "TOTEM_OF_UNDYING", "HEART_OF_THE_SEA", "DRAGON_EGG", "ELYTRA", "TRIDENT", "NETHERITE_SWORD", "BOW", "SHIELD",
            "ENCHANTED_GOLDEN_APPLE", "SUNFLOWER", "BEACON", "SKELETON_SKULL", "DRAGON_HEAD", "CAKE", "FIREWORK_STAR",
            "GLOW_INK_SAC", "SPYGLASS", "RECOVERY_COMPASS", "ECHO_SHARD", "CHERRY_LEAVES");

    private static final List<DCTagAnim.AnimType> TYPE_CYCLE = Arrays.asList(null,
            DCTagAnim.AnimType.RAINBOW, DCTagAnim.AnimType.FLOWING_GRADIENT,
            DCTagAnim.AnimType.SOLID_GRADIENT, DCTagAnim.AnimType.FLASHING_COLORS);

    private final DraCaveTags plugin;
    private final Map<UUID, Map<String, Draft>> drafts = new ConcurrentHashMap<>();

    public AdminConsole(DraCaveTags plugin) {
        this.plugin = plugin;
    }

    public void openEditor(Player player, String tagId, EditorReturn returnTarget, int page) {
        DCTag current = plugin.registry().get(tagId);
        if (current == null) {
            plugin.messages().send(player, "unknown-title", Locale.text("id", tagId));
            return;
        }
        Draft draft = drafts.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(current.id(), ignored -> new Draft(current));
        new PanelGui(plugin, player, draft, returnTarget, page).open();
    }

    public boolean ownsDraft(Player player, String tagId) {
        Map<String, Draft> map = drafts.get(player.getUniqueId());
        return map != null && map.containsKey(tagId);
    }

    private DCTag build(Draft draft) {
        return new DCTag(draft.id, draft.display, draft.description, draft.icon, draft.order,
                draft.defaultUnlocked, draft.permission, draft.animation, draft.offer, draft.colors,
                draft.shopHidden, draft.effects, draft.particle, draft.revision);
    }

    private void save(Player player, Draft draft, EditorReturn returnTarget, int page) {
        player.closeInventory();
        plugin.defEngine().update(build(draft), draft.revision).thenAccept(saved ->
                SchedulerUtil.runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (saved) {
                        player.sendMessage("§a称号 " + draft.id + " 已保存。");
                    } else {
                        plugin.messages().send(player, "custom-result-conflict");
                    }
                    drafts.getOrDefault(player.getUniqueId(), Map.of()).remove(draft.id);
                    new AdminScreen(plugin, player, page).open();
                }));
    }

    private void deleteTag(Player player, String tagId) {
        player.closeInventory();
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                boolean deleted = plugin.defStore().delete(tagId);
                if (deleted) {
                    try {
                        int removed = plugin.playerStore().removeTagFromAll(tagId);
                        if (removed > 0) {
                            player.sendMessage("§e已清理 " + removed + " 名玩家的该称号数据");
                        }
                    } catch (Exception cleanup) {
                        plugin.getLogger().warning("清理玩家称号数据失败 " + tagId + ": " + cleanup.getMessage());
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("删除称号失败 " + tagId + ": " + ex.getMessage());
            }
            SchedulerUtil.runTask(plugin, () -> {
                plugin.defEngine().reload().thenRun(() -> {
                    plugin.tagEngine().removeCachedTagFromAll(tagId);
                    player.sendMessage("§a已删除称号 " + tagId + "。");
                    new AdminScreen(plugin, player, 0).open();
                });
            });
        });
    }

    static final class Draft {
        final String id;
        String display;
        String icon;
        int order;
        boolean defaultUnlocked;
        String permission;
        DCTagAnim animation;
        DCTagOffer offer;
        List<String> colors = new ArrayList<>();
        List<String> description = new ArrayList<>();
        boolean shopHidden;
        List<DCTagPotion> effects = new ArrayList<>();
        DCTagPart particle;
        int revision;

        Draft(DCTag definition) {
            this.id = definition.id();
            this.display = definition.display();
            this.icon = definition.icon();
            this.order = definition.order();
            this.defaultUnlocked = definition.defaultUnlocked();
            this.permission = definition.permission();
            this.animation = definition.animation();
            this.offer = definition.purchaseOffer();
            this.colors = new ArrayList<>(definition.colors());
            this.description = new ArrayList<>(definition.description());
            this.shopHidden = definition.shopHidden();
            this.effects = new ArrayList<>(definition.potionEffects());
            this.particle = definition.particle();
            this.revision = definition.revision();
        }
    }

    public enum EditorReturn {
        ADMIN_SHOP,
        COMMAND
    }

    private final class PanelGui implements ClickableScreen {
        private final Player player;
        private final Draft draft;
        private final EditorReturn returnTarget;
        private final int page;
        private Inventory inventory;

        private PanelGui(DraCaveTags plugin, Player player, Draft draft, EditorReturn returnTarget, int page) {
            this.player = player;
            this.draft = draft;
            this.returnTarget = returnTarget;
            this.page = page;
        }

        void open() {
            inventory = Bukkit.createInventory(this, 54, MiniMessage.miniMessage().deserialize(plugin.messages().rawString("gui-admin-panel") + " <gray>" + draft.id));
            inventory.setItem(0, button(Material.ARROW, "<yellow>返回"));
            inventory.setItem(4, previewItem());
            inventory.setItem(8, button(Material.BARRIER, "<red>删除称号"));
            inventory.setItem(9, button(Material.NAME_TAG, "<yellow>称号文本\n<gray>当前：<white>" + plain(draft.display) + "\n<dark_gray>点击修改"));
            inventory.setItem(10, button(Material.ITEM_FRAME, "<yellow>图标\n<gray>当前：<white>" + draft.icon + "\n<dark_gray>点击修改"));
            inventory.setItem(11, button(Material.HOPPER, "<yellow>排序\n<gray>当前：<white>" + draft.order + "\n<dark_gray>点击修改"));
            inventory.setItem(12, button(Material.CLOCK, "<yellow>动画周期\n<gray>当前：<white>" + periodDisplay() + "\n<dark_gray>点击修改（秒）"));
            inventory.setItem(13, button(Material.GOLD_INGOT, "<yellow>价格\n<gray>当前：<white>" + offerDisplay() + "\n<dark_gray>点击修改（none 移除价格）"));
            inventory.setItem(14, button(Material.EMERALD, "<yellow>货币\n<gray>当前：<white>" + currencyDisplay() + "\n<dark_gray>点击切换"));
            inventory.setItem(15, button(Material.ENDER_EYE, "<yellow>商店隐藏\n<gray>当前：<white>" + (draft.shopHidden ? "隐藏" : "显示") + "\n<dark_gray>点击切换"));
            inventory.setItem(16, button(Material.COMMAND_BLOCK, "<yellow>购买权限\n<gray>当前：<white>" + (draft.permission.isEmpty() ? "无" : draft.permission) + "\n<dark_gray>点击修改（none 清除）"));
            inventory.setItem(17, button(Material.POTION, "<yellow>药水效果（多选）\n<gray>当前：<white>" + effectsDisplay() + "\n<dark_gray>点击打开选择面板"));
            inventory.setItem(49, button(Material.FIREWORK_STAR, "<yellow>粒子特效\n<gray>当前：<white>" + particleDisplay() + "\n<dark_gray>点击从预设中选择"));
            inventory.setItem(46, button(Material.BLAZE_ROD, "<yellow>动画类型\n<gray>当前：<white>" + typeDisplay()
                    + "\n<dark_gray>点击切换（渐变/闪烁需要至少 2 个颜色）"));
            inventory.setItem(47, button(Material.COMPASS, "<yellow>渐变方向\n<gray>当前：<white>" + modeDisplay()
                    + "\n<dark_gray>点击在循环/回弹之间切换"));
            inventory.setItem(18, button(Material.LIME_DYE, "<green>添加颜色\n<dark_gray>点击打开调色板"));
            for (int i = 0; i < 8; i++) {
                int slot = 19 + i;
                if (i < draft.colors.size()) {
                    String color = draft.colors.get(i);
                    inventory.setItem(slot, colored(color, i));
                } else {
                    inventory.setItem(slot, button(Material.GRAY_STAINED_GLASS_PANE, "<gray>空颜色槽 " + (i + 1)));
                }
            }
            for (int i = 0; i < 9 && i < draft.description.size(); i++) {
                inventory.setItem(27 + i, button(Material.MAP, "<gray>描述 " + (i + 1) + "\n<white>" + draft.description.get(i)));
            }
            inventory.setItem(45, button(Material.LIME_CONCRETE, "<green>保存并上传\n<dark_gray>保存到数据库并同步 tags.yml"));
            player.openInventory(inventory);
        }

        private ItemStack previewItem() {
            DCTag preview = build(draft);
            ItemStack item = ItemResolver.resolve(draft.icon);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(DCTagRenderer.component(preview, System.currentTimeMillis()).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    MiniMessage.miniMessage().deserialize("<gray>动画实时预览"),
                    MiniMessage.miniMessage().deserialize("<dark_gray>点击打开编辑面板")
            ));
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack colored(String hex, int index) {
            ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
            ItemMeta meta = item.getItemMeta();
            try {
                ((LeatherArmorMeta) meta).setColor(Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16)));
            } catch (NumberFormatException ignored) {
            }
            meta.displayName(MiniMessage.miniMessage().deserialize("<" + hex + ">颜色 " + (index + 1) + " (" + hex + ")")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    MiniMessage.miniMessage().deserialize("<dark_gray>左键移除"),
                    MiniMessage.miniMessage().deserialize("<dark_gray>右键右移 / Shift+左键左移")
            ));
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack button(Material material, String text) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            List<Component> lines = new ArrayList<>();
            for (String line : text.split("\n")) {
                lines.add(MiniMessage.miniMessage().deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.displayName(lines.get(0));
            meta.lore(lines.subList(1, lines.size()));
            item.setItemMeta(meta);
            return item;
        }

        private String plain(String display) {
            return DCTagRenderer.plain(
                    new DCTag(draft.id, display, List.of(), draft.icon, draft.order, false, "",
                            draft.animation == null ? null : draft.animation, null), System.currentTimeMillis());
        }

        private String periodDisplay() {
            if (draft.animation == null) {
                return "无动画";
            }
            return String.format("%.1f 秒", draft.animation.periodTicks() / 20.0);
        }

        private String offerDisplay() {
            return draft.offer == null ? "不可购买" : draft.offer.price().toPlainString();
        }

        private String currencyDisplay() {
            if (draft.offer == null) {
                return "-";
            }
            if (draft.offer.currency() == EcoType.ITEM) {
                return "item (" + draft.offer.itemMaterial() + ")";
            }
            return draft.offer.currency().id();
        }

        private String effectsDisplay() {
            if (draft.effects.isEmpty()) {
                return "无";
            }
            List<String> parts = new ArrayList<>();
            for (DCTagPotion effect : draft.effects) {
                parts.add(effect.effectType() + " " + effect.level());
            }
            return String.join("、", parts);
        }

        private String particleDisplay() {
            return draft.particle == null ? "无" : draft.particle.particleType();
        }

        private String typeDisplay() {
            if (draft.animation == null) {
                return "无动画";
            }
            return switch (draft.animation.type()) {
                case FLOWING_GRADIENT -> "流动渐变";
                case SOLID_GRADIENT -> "整体渐变";
                case FLASHING_COLORS -> "颜色闪烁";
                case RAINBOW -> "彩虹";
                case TEXT_FRAMES -> "帧动画（仅 tags.yml 可编辑）";
            };
        }

        private String modeDisplay() {
            if (draft.animation == null) {
                return "-";
            }
            return draft.animation.mode() == DCTagAnim.GradientStyle.PINGPONG ? "回弹" : "循环";
        }

        private void syncAnimation() {
            if (draft.animation == null) {
                if (draft.colors.size() >= 2) {
                    draft.animation = new DCTagAnim(draft.colors, DEFAULT_PERIOD_TICKS);
                }
                return;
            }
            DCTagAnim.AnimType type = draft.animation.type();
            if (type == DCTagAnim.AnimType.RAINBOW || type == DCTagAnim.AnimType.TEXT_FRAMES) {
                return;
            }
            if (draft.colors.size() < 2) {
                draft.animation = null;
                return;
            }
            draft.animation = rebuild(type, draft.animation.periodTicks(), draft.animation.mode());
        }

        private DCTagAnim rebuild(DCTagAnim.AnimType type, int periodTicks, DCTagAnim.GradientStyle mode) {
            return new DCTagAnim(type, draft.colors,
                    draft.animation == null ? List.of() : draft.animation.frames(), periodTicks, mode);
        }

        private void cycleAnimationType() {
            if (draft.animation != null && draft.animation.type() == DCTagAnim.AnimType.TEXT_FRAMES) {
                player.sendMessage("§c帧动画请在 tags.yml 中编辑，面板不会改动它。");
                return;
            }
            List<DCTagAnim.AnimType> order = new ArrayList<>(TYPE_CYCLE);
            if (draft.colors.size() < 2) {
                order.removeIf(type -> type != null && type != DCTagAnim.AnimType.RAINBOW);
                player.sendMessage("§7颜色不足 2 个，只能在「无动画 / 彩虹」之间切换。");
            }
            DCTagAnim.AnimType current = draft.animation == null ? null : draft.animation.type();
            DCTagAnim.AnimType next = order.get((order.indexOf(current) + 1) % order.size());
            int period = draft.animation == null ? DEFAULT_PERIOD_TICKS : draft.animation.periodTicks();
            DCTagAnim.GradientStyle mode = draft.animation == null
                    ? DCTagAnim.GradientStyle.CYCLE : draft.animation.mode();
            draft.animation = next == null ? null : rebuild(next, period, mode);
        }

        @Override
        public void click(int rawSlot, ClickType clickType) {
            if (rawSlot == 0) {
                new AdminScreen(plugin, player, page).open();
            } else if (rawSlot == 8) {
                deleteTag(player, draft.id);
            } else if (rawSlot == 4) {
                open();
                open();
            } else if (rawSlot == 9) {
                prompt("§e请输入新的称号文本（纯文本）：", (p, value) -> {
                    if (value.equalsIgnoreCase("none")) {
                        draft.display = "";
                        p.sendMessage("§c文本不能为空。");
                        open();
                        return;
                    }
                    String text = MiniMessage.miniMessage().escapeTags(value.trim());
                    if (text.isEmpty() || text.codePointCount(0, text.length()) > 64) {
                        p.sendMessage("§c文本长度必须为 1 至 64 个字符。");
                        open();
                        return;
                    }
                    draft.display = text;
                    open();
                });
            } else if (rawSlot == 10) {
                openIconPicker();
            } else if (rawSlot == 11) {
                prompt("§e请输入排序值（整数）：", (p, value) -> {
                    try {
                        draft.order = Integer.parseInt(value.trim());
                    } catch (NumberFormatException ex) {
                        p.sendMessage("§c请输入整数。");
                        open();
                        return;
                    }
                    open();
                });
            } else if (rawSlot == 12) {
                prompt("§e请输入动画周期（秒，0.2-60；none 移除动画）：", (p, value) -> {
                    if (value.equalsIgnoreCase("none")) {
                        draft.animation = null;
                    } else {
                        try {
                            double seconds = Double.parseDouble(value.trim());
                            if (seconds < 0.2 || seconds > 60.0) {
                                p.sendMessage("§c周期必须在 0.2 至 60 秒之间。");
                                open();
                                return;
                            }
                            int ticks = (int) Math.round(seconds * 20.0);
                            syncAnimation();
                            if (draft.animation == null) {
                                p.sendMessage("§c当前称号没有动画，请先添加至少 2 个颜色或切换动画类型。");
                                open();
                                return;
                            }
                            draft.animation = new DCTagAnim(draft.animation.type(), draft.animation.colors(),
                                    draft.animation.frames(), ticks, draft.animation.mode());
                        } catch (NumberFormatException ex) {
                            p.sendMessage("§c请输入合法数字。");
                            open();
                            return;
                        }
                    }
                    open();
                });
            } else if (rawSlot == 13) {
                prompt("§e请输入价格（数字；none 移除价格使称号不可购买）：", (p, value) -> {
                    if (value.equalsIgnoreCase("none")) {
                        draft.offer = null;
                    } else {
                        try {
                            BigDecimal price = new BigDecimal(value.trim());
                            draft.offer = new DCTagOffer(
                                    draft.offer == null ? EcoType.VAULT : draft.offer.currency(), price);
                        } catch (IllegalArgumentException ex) {
                            p.sendMessage("§c价格不合法：" + ex.getMessage());
                            open();
                            return;
                        }
                    }
                    open();
                });
            } else if (rawSlot == 14) {
                EcoType current = draft.offer == null ? EcoType.VAULT : draft.offer.currency();
                EcoType next = nextCurrency(current);
                if (next == EcoType.ITEM) {
                    promptItemMaterial(current);
                } else {
                    draft.offer = draft.offer == null ? null : new DCTagOffer(next, draft.offer.price());
                    open();
                }
            } else if (rawSlot == 15) {
                draft.shopHidden = !draft.shopHidden;
                open();
            } else if (rawSlot == 16) {
                prompt("§e请输入购买所需权限节点（none 清除）：", (p, value) -> {
                    draft.permission = value.equalsIgnoreCase("none") ? "" : value.trim();
                    open();
                });
            } else if (rawSlot == 17) {
                Map<String, Integer> current = new java.util.LinkedHashMap<>();
                for (DCTagPotion e : draft.effects) {
                    current.put(e.effectType(), e.level());
                }
                new PotionPickerScreen(plugin, player, current, (p, effects) -> {
                    List<DCTagPotion> list = new ArrayList<>();
                    effects.forEach((type, level) -> list.add(new DCTagPotion(type, level)));
                    draft.effects = list;
                    open();
                }, this::open).open();
            } else if (rawSlot == 49) {
                new ParticleScreen(plugin, player, particle -> {
                    draft.particle = particle;
                    open();
                }, this::open).open();
            } else if (rawSlot == 18) {
                if (draft.colors.size() >= 8) {
                    player.sendMessage("§c最多 8 个颜色。");
                    return;
                }
                new ColorScreen(plugin, player, color -> {
                    draft.colors.add(color);
                    syncAnimation();
                    open();
                }, this::open).open();
            } else if (rawSlot >= 19 && rawSlot <= 26) {
                int index = rawSlot - 19;
                if (index < draft.colors.size()) {
                    if (clickType == ClickType.RIGHT) {
                        moveColor(index, 1);
                    } else if (clickType == ClickType.SHIFT_LEFT) {
                        moveColor(index, -1);
                    } else {
                        draft.colors.remove(index);
                    }
                    syncAnimation();
                    open();
                }
            } else if (rawSlot == 46) {
                cycleAnimationType();
                open();
            } else if (rawSlot == 47) {
                if (draft.animation != null) {
                    draft.animation = new DCTagAnim(draft.animation.type(), draft.animation.colors(),
                            draft.animation.frames(), draft.animation.periodTicks(),
                            draft.animation.mode() == DCTagAnim.GradientStyle.PINGPONG
                                    ? DCTagAnim.GradientStyle.CYCLE : DCTagAnim.GradientStyle.PINGPONG);
                }
                open();
            } else if (rawSlot == 45) {
                save(player, draft, returnTarget, page);
            }
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
                draft.icon = material.name();
                open();
            }, this::open, this::promptIcon).open();
        }

        private void promptIcon() {
            prompt("§e请输入物品材质名称（如 NAME_TAG、head:纹理 或 base64:序列化，none 恢复默认）：", (p, value) -> {
                if (value.equalsIgnoreCase("none")) {
                    draft.icon = "NAME_TAG";
                } else {
                    if (!ItemResolver.isValid(value)) {
                        p.sendMessage("§c无效的物品材质。");
                        open();
                        return;
                    }
                    draft.icon = value;
                }
                open();
            });
        }

        private void promptItemMaterial(EcoType previous) {
            prompt("§e请输入物品材质名称（如 DIAMOND、EMERALD，none 返回上一种货币）：", (p, value) -> {
                if (value.equalsIgnoreCase("none")) {
                    if (draft.offer != null) {
                        draft.offer = new DCTagOffer(previous, draft.offer.price());
                    }
                    open();
                    return;
                }
                Material material = Material.matchMaterial(value.toUpperCase());
                if (material == null || material.isAir()) {
                    p.sendMessage("§c无效的物品材质：" + value);
                    open();
                    return;
                }
                BigDecimal price = draft.offer == null ? BigDecimal.ONE : draft.offer.price();
                draft.offer = DCTagOffer.item(material.name(), price.intValue());
                open();
            });
        }

        private void moveColor(int index, int direction) {
            int target = index + direction;
            if (target < 0 || target >= draft.colors.size()) {
                return;
            }
            String color = draft.colors.remove(index);
            draft.colors.add(target, color);
        }

        private EcoType nextCurrency(EcoType current) {
            EcoType[] values = EcoType.values();
            for (int i = 0; i < values.length; i++) {
                if (values[i] == current) {
                    return values[(i + 1) % values.length];
                }
            }
            return EcoType.VAULT;
        }

        private void prompt(String message, java.util.function.BiConsumer<Player, String> handler) {
            player.closeInventory();
            plugin.chatPrompt().prompt(player, message, (p, value) -> {
                handler.accept(p, value);
            }, true);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public void close() {
        for (UUID playerId : drafts.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                plugin.chatPrompt().clear(player);
            }
        }
        drafts.clear();
    }
}
