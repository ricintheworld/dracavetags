package com.dracave.tags.cmd;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.api.DCTagResult;
import com.dracave.tags.config.Locale;
import com.dracave.tags.config.Cfg;
import com.dracave.tags.engine.CustomEngine;
import com.dracave.tags.handlers.CustomDraft;
import com.dracave.tags.handlers.CustomDCTag;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.DCTagAnim;
import com.dracave.tags.handlers.DCTagOffer;
import com.dracave.tags.handlers.DCTagPart;
import com.dracave.tags.handlers.DCTagPotion;
import com.dracave.tags.handlers.DCTagType;
import com.dracave.tags.handlers.EcoType;
import com.dracave.tags.handlers.PlayerData;
import com.dracave.tags.handlers.RewardCfg;
import com.dracave.tags.handlers.RewardKind;
import com.dracave.tags.panel.AdminConsole;
import com.dracave.tags.render.DCTagRenderer;
import com.dracave.tags.screen.AdminScreen;
import com.dracave.tags.screen.CustomScreen;
import com.dracave.tags.screen.MainScreen;
import com.dracave.tags.screen.RewardScreen;
import com.dracave.tags.screen.ShopScreen;
import com.dracave.tags.screen.VaultScreen;
import com.dracave.tags.screen.ViewScreen;
import com.dracave.tags.storage.RankEntry;
import com.dracave.tags.util.ItemResolver;
import com.dracave.tags.util.SchedulerUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class DCTagCommand implements CommandExecutor, TabCompleter {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");

    private final DraCaveTags plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();

    public DCTagCommand(DraCaveTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (plugin.tagEngine() == null) {
            plugin.messages().send(sender, "unavailable");
            return true;
        }
        String sub = args.length == 0 ? "open" : args[0].toLowerCase(java.util.Locale.ROOT);
        boolean playerCommand = sub.equals("open") || sub.equals("shop") || sub.equals("custom") || sub.equals("view")
                || sub.equals("wear") || sub.equals("clear") || sub.equals("reward") || sub.equals("ranking");
        if (!playerCommand && !sender.hasPermission("dracave.tags.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (playerCommand && !sender.hasPermission("dracave.tags.use")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        try {
            switch (sub) {
                case "open" -> open(sender, args);
                case "listtitle", "list" -> listTags(sender);
                case "shop" -> shop(sender);
                case "adminshop", "adminShop" -> adminShop(sender);
                case "create" -> create(sender, args);
                case "add" -> add(sender, args);
                case "del" -> del(sender, args);
                case "setdescription", "setDescription" -> setDescription(sender, args);
                case "addpermission", "addPermission" -> addPermission(sender, args);
                case "settitlebuff", "setTitleBuff" -> setTitleBuff(sender, args);
                case "delbuff", "delBuff" -> delBuff(sender, args);
                case "settitleparticle", "setTitleParticle" -> setTitleParticle(sender, args);
                case "removetitleparticle", "removeTitleParticle" -> removeTitleParticle(sender, args);
                case "reload" -> reload(sender);
                case "set" -> set(sender, args);
                case "addplayertitle", "addPlayerTitle" -> addPlayerTitle(sender, args);
                case "addcoin", "addCoin" -> addCoin(sender, args);
                case "subtractcoin", "subtractCoin" -> subtractCoin(sender, args);
                case "changeitem", "changeItem" -> changeItem(sender, args);
                case "addreward", "addReward" -> addReward(sender, args);
                case "randomcard", "randomCard" -> randomCard(sender, args);
                case "setcustom", "setCustom" -> setCustom(sender, args);
                case "addcustom", "addCustom" -> addCustom(sender, args);
                case "custom" -> custom(sender, args);
                case "view" -> view(sender, args);
                case "wear" -> wear(sender, args);
                case "clear" -> clear(sender);
                case "reward" -> reward(sender);
                case "ranking" -> ranking(sender);
                case "upload" -> upload(sender, args);
                case "panel" -> panel(sender, args, false);
                case "panel-id" -> panel(sender, args, true);
                case "panel-edit" -> panelEdit(sender, args);
                case "help" -> help(sender);
                default -> help(sender);
            }
        } catch (IllegalArgumentException ex) {
            plugin.messages().send(sender, "operation-failed");
            sender.sendMessage("§c" + ex.getMessage());
        }
        return true;
    }

    private void open(CommandSender sender, String[] args) {
        if (args.length >= 2 && sender.hasPermission("dracave.tags.admin")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                plugin.messages().send(sender, "unknown-player", Locale.text("player", args[1]));
                return;
            }
            new VaultScreen(plugin, target, 0).open();
            return;
        }
        requirePlayer(sender, p -> new VaultScreen(plugin, p, 0).open());
    }

    private void shop(CommandSender sender) {
        requirePlayer(sender, p -> new ShopScreen(plugin, p, 0).open());
    }

    private void adminShop(CommandSender sender) {
        requirePlayer(sender, p -> new AdminScreen(plugin, p, 0).open());
    }

    private void reward(CommandSender sender) {
        requirePlayer(sender, p -> new RewardScreen(plugin, p).open());
    }

    private void wear(CommandSender sender, String[] args) {
        requirePlayer(sender, p -> {
            if (args.length < 2) {
                new VaultScreen(plugin, p, 0).open();
                return;
            }
            String tagId = args[1];
            if (tagId.equalsIgnoreCase("none")) {
                plugin.tagEngine().clear(p.getUniqueId()).thenAccept(result -> {
                    if (result == DCTagResult.SUCCESS) {
                        plugin.messages().send(p, "cleared");
                    } else if (result == DCTagResult.CANCELLED) {
                        plugin.messages().send(p, "operation-failed");
                    }
                });
                return;
            }
            plugin.tagEngine().equip(p.getUniqueId(), tagId).thenAccept(result -> {
                if (result == DCTagResult.SUCCESS) {
                    plugin.messages().send(p, "equipped",
                            Locale.parsed("title", renderedTag(tagId)));
                } else if (result == DCTagResult.NOT_UNLOCKED) {
                    plugin.messages().send(p, "locked");
                } else if (result == DCTagResult.TITLE_NOT_FOUND) {
                    plugin.messages().send(p, "unknown-title", Locale.text("id", tagId));
                } else if (result == DCTagResult.COOLDOWN) {
                } else {
                    plugin.messages().send(p, "operation-failed");
                }
            });
        });
    }

    private void clear(CommandSender sender) {
        requirePlayer(sender, p -> plugin.tagEngine().clear(p.getUniqueId()).thenAccept(result -> {
            if (result == DCTagResult.SUCCESS) {
                PlayerData data = plugin.tagEngine().getCached(p.getUniqueId());
                if (data == null || data.equippedId() == null) {
                    plugin.messages().send(p, "cleared-none");
                } else {
                    plugin.messages().send(p, "cleared");
                }
            }
        }));
    }

    private void listTags(CommandSender sender) {
        List<DCTag> tags = plugin.registry().all();
        if (tags.isEmpty()) {
            sender.sendMessage("§e当前没有任何称号，请先在 tags/ 中配置并执行 /dctags upload。");
            return;
        }
        sender.sendMessage("§e===== 服务器称号列表（共 " + tags.size() + " 个）=====");
        for (DCTag tag : tags) {
            StringBuilder line = new StringBuilder();
            line.append(DCTagRenderer.miniMessage(tag, System.currentTimeMillis()))
                    .append(" §7(").append(tag.id()).append(")");
            if (tag.purchasable()) {
                line.append(" §f价格：").append(tag.purchaseOffer().price().toPlainString())
                        .append(" ").append(tag.purchaseOffer().currency().id());
            }
            if (tag.shopHidden()) {
                line.append(" §7[商店隐藏]");
            }
            sender.sendMessage(line.toString());
        }
    }

    private void view(CommandSender sender, String[] args) {
        if (args.length >= 3) {
            if (!sender.hasPermission("dracave.tags.admin")) {
                plugin.messages().send(sender, "no-permission");
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            if (!target.hasPlayedBefore() && Bukkit.getPlayerExact(args[2]) == null) {
                plugin.messages().send(sender, "unknown-player", Locale.text("player", args[2]));
                return;
            }
            plugin.tagEngine().load(target.getUniqueId()).thenAccept(data -> {
                if (data == null) {
                    plugin.messages().send(sender, "unavailable");
                    return;
                }
                sender.sendMessage("§e玩家 " + args[2] + " 的称号列表（共 " + data.unlocked().size() + " 个）：");
                for (String id : data.unlocked().stream().sorted().toList()) {
                    DCTag tag = plugin.registry().get(id);
                    if (tag == null) {
                        continue;
                    }
                    String equipped = id.equals(data.equippedId()) ? " §a[当前穿戴]" : "";
                    sender.sendMessage(DCTagRenderer.miniMessage(tag, System.currentTimeMillis())
                            + " §7(" + id + ")" + equipped);
                }
            });
            return;
        }
        if (args.length == 2) {
            String type = args[1].toLowerCase(java.util.Locale.ROOT);
            switch (type) {
                case "shop" -> requirePlayer(sender, p -> new ShopScreen(plugin, p, 0).open());
                case "reward" -> requirePlayer(sender, p -> new RewardScreen(plugin, p).open());
                default -> {
                    if (!sender.hasPermission("dracave.tags.admin")) {
                        plugin.messages().send(sender, "no-permission");
                        return;
                    }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    if (!target.hasPlayedBefore() && Bukkit.getPlayerExact(args[1]) == null) {
                        plugin.messages().send(sender, "unknown-player", Locale.text("player", args[1]));
                        return;
                    }
                    if (sender instanceof Player admin) {
                        new ViewScreen(plugin, admin, target.getUniqueId(), args[1], 0).open();
                    } else {
                        viewPlayer(sender, target);
                    }
                }
            }
            return;
        }
        requirePlayer(sender, p -> new MainScreen(plugin, p).open());
    }

    private void viewPlayer(CommandSender sender, OfflinePlayer target) {
        plugin.tagEngine().load(target.getUniqueId()).thenAccept(data -> {
            if (data == null) {
                plugin.messages().send(sender, "unavailable");
                return;
            }
            sender.sendMessage("§e玩家 " + target.getName() + " 的称号列表（共 " + data.unlocked().size() + " 个）：");
            for (String id : data.unlocked().stream().sorted().toList()) {
                DCTag tag = plugin.registry().get(id);
                if (tag == null) {
                    continue;
                }
                String equipped = id.equals(data.equippedId()) ? " §a[当前穿戴]" : "";
                sender.sendMessage(DCTagRenderer.miniMessage(tag, System.currentTimeMillis())
                        + " §7(" + id + ")" + equipped);
            }
        });
    }

    private void custom(CommandSender sender, String[] args) {
        requirePlayer(sender, p -> {
            if (args.length == 1) {
                new CustomScreen(plugin, p, 0).open();
                return;
            }
            String action = args[1].toLowerCase(java.util.Locale.ROOT);
            if (action.equals("delete") && args.length >= 3) {
                plugin.customEngine().delete(p, args[2]).thenAccept(result ->
                        SchedulerUtil.runTask(plugin, () -> sendCustomResult(p, result)));
            } else if (action.equals("create") && args.length >= 4) {
                createCustom(p, args, 2, false);
            } else if (action.equals("edit") && args.length >= 5) {
                createCustom(p, args, 3, true);
            } else if (action.equals("create") || action.equals("edit")) {
                plugin.messages().send(p, "custom-command-help");
            } else {
                createQuick(p, args[1]);
            }
        });
    }

    private void createQuick(Player player, String name) {
        plugin.customEngine().create(player, CustomDraft.staticTag(name, "#55FFFF", "NAME_TAG"))
                .thenAccept(result -> SchedulerUtil.runTask(plugin, () -> sendCustomResult(player, result)));
    }

    private void createCustom(Player player, String[] args, int typeIndex, boolean editing) {
        String typeName = args[typeIndex].toLowerCase(java.util.Locale.ROOT);
        DCTagType type = DCTagType.parse(typeName);
        if (type == null) {
            plugin.messages().send(player, "custom-result-invalid");
            return;
        }
        int base = typeIndex + 1;
        CustomDraft draft = switch (type) {
            case STATIC -> {
                if (args.length < base + 2) {
                    yield null;
                }
                String color = COLOR.matcher(args[base].toUpperCase()).matches() ? args[base].toUpperCase() : "#55FFFF";
                yield CustomDraft.staticTag(args[base + 1], color, "NAME_TAG");
            }
            case FLOWING_GRADIENT -> {
                if (args.length < base + 3) {
                    yield null;
                }
                yield CustomDraft.gradient(args[base + 2], parseColors(args[base + 1]), parsePeriod(args[base]), "NAME_TAG");
            }
            case RAINBOW -> {
                if (args.length < base + 2) {
                    yield null;
                }
                yield CustomDraft.rainbow(args[base + 1], parsePeriod(args[base]), "NAME_TAG");
            }
            case FLASHING_COLORS -> {
                if (args.length < base + 3) {
                    yield null;
                }
                yield CustomDraft.flash(args[base + 2], parseColors(args[base + 1]), parsePeriod(args[base]), "NAME_TAG");
            }
            case TEXT_FRAMES -> {
                if (args.length < base + 3) {
                    yield null;
                }
                yield CustomDraft.frames(args[base + 2], Arrays.asList(args[base + 1].split("\\|")), parsePeriod(args[base]), "NAME_TAG");
            }
        };
        if (draft == null) {
            plugin.messages().send(player, "custom-command-help");
            return;
        }
        var result = editing
                ? plugin.customEngine().update(player, args[2], draft)
                : plugin.customEngine().create(player, draft);
        result.thenAccept(r -> SchedulerUtil.runTask(plugin, () -> sendCustomResult(player, r)));
    }

    private List<String> parseColors(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(c -> c.toUpperCase(java.util.Locale.ROOT))
                .peek(c -> {
                    if (!COLOR.matcher(c).matches()) {
                        throw new IllegalArgumentException("颜色必须是 #RRGGBB: " + c);
                    }
                })
                .toList();
    }

    private int parsePeriod(String value) {
        int period = Integer.parseInt(value);
        if (period < 5 || period > 200) {
            throw new IllegalArgumentException("周期必须在 5-200 游戏刻之间");
        }
        return period;
    }

    private void sendCustomResult(Player player, CustomEngine.Result result) {
        if (result == CustomEngine.Result.SUCCESS) {
            plugin.messages().send(player, "custom-created");
        } else {
            plugin.messages().send(player, "custom-result-" + result.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
        }
    }

    private void add(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§e用法：/dctags add <货币类型 vault|playerpoints|coin|item> <称号名称> <价格> [天数] [隐藏 true|false] [玩家名]");
            sender.sendMessage("§7物品购买（item）：手持支付物执行命令，价格 = 所需数量");
            return;
        }
        EcoType currency;
        try {
            currency = EcoType.parse(args[1]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§c未知货币类型：" + args[1]);
            return;
        }
        final String itemMaterial;
        if (currency == EcoType.ITEM) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§c物品购买需要在游戏内执行（使用主手物品作为支付物）。");
                return;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                sender.sendMessage("§c请手持要作为支付物的物品（如钻石）。");
                return;
            }
            itemMaterial = hand.getType().name();
        } else {
            itemMaterial = null;
        }
        String name = args[2];
        BigDecimal price;
        try {
            price = new BigDecimal(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§c价格不是合法数字。");
            return;
        }
        int days = args.length >= 5 ? parseInt(args[4], 0) : 0;
        boolean hidden = args.length >= 6 && Boolean.parseBoolean(args[5]);
        DCTagOffer offer = currency == EcoType.ITEM
                ? new DCTagOffer(EcoType.ITEM, price, itemMaterial)
                : new DCTagOffer(currency, price);
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                DCTag definition = new DCTag(
                        generateId(name), mini.escapeTags(name), List.of("<gray>通过 /dctags add 创建"),
                        "NAME_TAG", 0, false, "", new DCTagAnim(List.of("#7AFBFF", "#B97AFF"), 40),
                        offer, List.of("#7AFBFF", "#B97AFF"), hidden, List.of(), null, 0);
                plugin.defStore().upsertAll(List.of(definition));
                plugin.defEngine().reload().thenRun(() -> {
                    String priceDisplay = currency == EcoType.ITEM
                            ? price.toPlainString() + " × " + itemMaterial
                            : price + " " + currency.id();
                    sender.sendMessage("§a已创建称号 §f" + name + " §a(ID: " + definition.id() + "，价格：" + priceDisplay + ")。");
                });
                if (args.length >= 7) {
                    grantToPlayer(sender, args[6], definition.id(), days, true);
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("创建称号失败: " + ex.getMessage());
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }

    private void create(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§e用法：/dctags create <称号文本> <颜色(#或逗号分隔hex色号)> <购买方式 vault|coin|point|item> <价格> [item物品]");
            return;
        }
        String rawText = args[1];
        String colorArg = args[2];
        EcoType currency;
        try {
            currency = EcoType.parse(args[3]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§c未知货币类型：" + args[3]);
            return;
        }
        BigDecimal price;
        try {
            price = new BigDecimal(args[4]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§c价格不是合法数字。");
            return;
        }
        String itemMaterial = null;
        if (currency == EcoType.ITEM) {
            if (args.length < 6) {
                sender.sendMessage("§c物品购买需要指定物品材质（如 minecraft:diamond）。");
                return;
            }
            itemMaterial = args[5];
            String lowerItem = itemMaterial.toLowerCase(java.util.Locale.ROOT);
            boolean isCustom = lowerItem.contains(":") && !lowerItem.startsWith("minecraft:");
            if (lowerItem.startsWith("minecraft:")) {
                itemMaterial = itemMaterial.substring(10);
            }
            if (!isCustom) {
                itemMaterial = itemMaterial.toUpperCase(java.util.Locale.ROOT);
                if (Material.matchMaterial(itemMaterial) == null) {
                    sender.sendMessage("§c未知物品材质：" + args[5]);
                    return;
                }
            }
        }
        DCTagOffer offer = currency == EcoType.ITEM
                ? new DCTagOffer(EcoType.ITEM, price, itemMaterial)
                : new DCTagOffer(currency, price);
        java.util.List<String> colors = new java.util.ArrayList<>();
        String display;
        if ("#".equals(colorArg)) {
            display = mini.escapeTags(rawText);
        } else {
            for (String part : colorArg.split(",")) {
                String c = part.trim();
                if (c.startsWith("#")) {
                    colors.add(c);
                } else if (!c.isEmpty()) {
                    colors.add("#" + c);
                }
            }
            if (colors.isEmpty()) {
                display = mini.escapeTags(rawText);
            } else if (colors.size() >= 2) {
                display = "<gradient:" + colors.get(0) + ":" + colors.get(colors.size() - 1) + ">"
                        + mini.escapeTags(rawText) + "</gradient>";
            } else {
                display = "<" + colors.get(0) + ">" + mini.escapeTags(rawText) + "</" + colors.get(0) + ">";
            }
        }
        String finalItemMaterial = itemMaterial;
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                DCTag definition = new DCTag(
                        generateId(rawText), display, java.util.List.of("<gray>通过 /dctags create 创建"),
                        "NAME_TAG", 0, false, "", null,
                        offer, colors, false, java.util.List.of(), null, 0);
                java.io.File ymlFile = new java.io.File(plugin.getDataFolder(), "tags.yml");
                com.dracave.tags.config.DCTagYamlLoader parser = new com.dracave.tags.config.DCTagYamlLoader();
                com.dracave.tags.config.DCTagYamlLoader.ParseResult parsed = parser.parse(ymlFile);
                java.util.List<DCTag> all = new java.util.ArrayList<>(parsed.definitions());
                all.add(definition);
                new com.dracave.tags.config.DCTagYamlWriter().writeAll(all, ymlFile);
                plugin.defEngine().upload().thenRun(() ->
                        SchedulerUtil.runTask(plugin, () -> {
                            String priceDisplay = currency == EcoType.ITEM
                                    ? price.toPlainString() + " × " + finalItemMaterial
                                    : price + " " + currency.id();
                            sender.sendMessage("§a已创建称号 §f" + rawText + " §a(ID: " + definition.id() + "，价格：" + priceDisplay + ")。");
                        }));
            } catch (Exception ex) {
                plugin.getLogger().severe("创建称号失败: " + ex.getMessage());
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }

    private void ranking(CommandSender sender) {
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                List<RankEntry> list = plugin.playerStore().ranking(10);
                SchedulerUtil.runTask(plugin, () -> {
                    sender.sendMessage("§e§m-------------§f[§e称号数量排行榜§f]§e§m-------------");
                    if (list.isEmpty()) {
                        sender.sendMessage("§7暂无数据");
                        return;
                    }
                    int rank = 1;
                    for (RankEntry entry : list) {
                        OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.playerId());
                        String name = offline.getName() == null
                                ? entry.playerId().toString().substring(0, 8) : offline.getName();
                        sender.sendMessage("§e" + rank + ". §f" + name + " §7（" + entry.unlockedCount() + " 个称号）");
                        rank++;
                    }
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("查询称号排行榜失败: " + ex.getMessage());
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }

    private String generateId(String name) {
        String slug = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "").replaceAll("[-_]+", "-");
        if (slug.length() > 20) {
            slug = slug.substring(0, 20);
        }
        if (slug.isEmpty()) {
            slug = "tag";
        }
        String id = slug;
        for (int i = 0; i < 10; i++) {
            if (plugin.registry().get(id) == null && VALID_ID.matcher(id).matches()) {
                return id;
            }
            id = slug + "_" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0x10000));
        }
        throw new IllegalArgumentException("无法生成唯一称号 ID，请重试");
    }

    private void del(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e用法：/dctags del <称号ID>");
            return;
        }
        String tagId = args[1].toLowerCase(java.util.Locale.ROOT);
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                boolean deleted = plugin.defStore().delete(tagId);
                if (deleted) {
                    try {
                        int removed = plugin.playerStore().removeTagFromAll(tagId);
                        if (removed > 0) {
                            sender.sendMessage("§e已清理 " + removed + " 名玩家的该称号数据");
                        }
                    } catch (Exception cleanup) {
                        plugin.getLogger().warning("清理玩家称号数据失败 " + tagId + ": " + cleanup.getMessage());
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("删除称号失败 " + tagId + ": " + ex.getMessage());
                plugin.messages().send(sender, "operation-failed");
                return;
            }
            plugin.tagEngine().removeCachedTagFromAll(tagId);
            SchedulerUtil.runTask(plugin, () -> plugin.defEngine().reload().thenRun(() -> {
                plugin.messages().send(sender, "title-deleted", Locale.text("id", tagId));
            }));
        });
    }

    private void setDescription(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctags setDescription <称号ID> <描述>（多行用 \\n 分隔，支持 MiniMessage）");
            return;
        }
        String tagId = args[1].toLowerCase(java.util.Locale.ROOT);
        modifyDefinition(sender, tagId, definition -> {
            String raw = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            List<String> lines = Arrays.stream(raw.split("\\\\n")).limit(64).toList();
            for (String line : lines) {
                if (line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65535) {
                    throw new IllegalArgumentException("描述单行过长");
                }
            }
            return new DCTag(definition.id(), definition.display(), lines, definition.icon(),
                    definition.order(), definition.defaultUnlocked(), definition.permission(), definition.animation(),
                    definition.purchaseOffer(), definition.colors(), definition.shopHidden(),
                    definition.potionEffects(), definition.particle(), definition.revision());
        }, "description-set", Locale.text("id", tagId));
    }

    private void addPermission(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctags addPermission <称号名称或ID> <所需权限>（none 清除）");
            return;
        }
        String tagId = resolveTagId(args[1]);
        String permission = args[2].equalsIgnoreCase("none") ? "" : args[2];
        modifyDefinition(sender, tagId, definition -> new DCTag(
                definition.id(), definition.display(), definition.description(), definition.icon(),
                definition.order(), definition.defaultUnlocked(), permission, definition.animation(),
                definition.purchaseOffer(), definition.colors(), definition.shopHidden(),
                definition.potionEffects(), definition.particle(), definition.revision()),
                "permission-set", Locale.text("id", tagId), Locale.text("permission", permission.isEmpty() ? "无" : permission));
    }

    private void setTitleBuff(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§e用法：/dctags setTitleBuff <称号ID> <类型: POTION_EFFECT|POTION> <效果名> [等级]");
            return;
        }
        String tagId = args[1].toLowerCase(java.util.Locale.ROOT);
        String type = args[2].toLowerCase(java.util.Locale.ROOT);
        if (!type.equals("potion_effect") && !type.equals("potion") && !type.equals("药水")) {
            sender.sendMessage("§c当前仅支持药水效果类型（POTION_EFFECT）。");
            return;
        }
        PotionEffectType effectType = PotionEffectType.getByName(args[3].toUpperCase());
        if (effectType == null) {
            sender.sendMessage("§c未知药水效果：" + args[3]);
            return;
        }
        int level = args.length >= 5 ? parseInt(args[4], 1) : 1;
        if (level < 1 || level > 255) {
            sender.sendMessage("§c等级必须为 1-255。");
            return;
        }
        int finalLevel = level;
        modifyDefinition(sender, tagId, definition -> {
            List<DCTagPotion> effects = new ArrayList<>(definition.potionEffects());
            effects.removeIf(e -> e.effectType().equals(effectType.getName()));
            effects.add(new DCTagPotion(effectType.getName(), finalLevel));
            return new DCTag(definition.id(), definition.display(), definition.description(), definition.icon(),
                    definition.order(), definition.defaultUnlocked(), definition.permission(), definition.animation(),
                    definition.purchaseOffer(), definition.colors(), definition.shopHidden(),
                    effects, definition.particle(), definition.revision());
        }, "buff-added", Locale.text("id", tagId), Locale.text("effect", effectType.getName() + " " + finalLevel));
    }

    private void delBuff(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctags delBuff <称号ID> <效果名>");
            return;
        }
        String tagId = args[1].toLowerCase(java.util.Locale.ROOT);
        String effectName = args[2].toUpperCase(java.util.Locale.ROOT);
        modifyDefinition(sender, tagId, definition -> {
            List<DCTagPotion> effects = new ArrayList<>(definition.potionEffects());
            if (!effects.removeIf(e -> e.effectType().equals(effectName))) {
                throw new IllegalArgumentException("该称号没有 " + effectName + " 效果");
            }
            return new DCTag(definition.id(), definition.display(), definition.description(), definition.icon(),
                    definition.order(), definition.defaultUnlocked(), definition.permission(), definition.animation(),
                    definition.purchaseOffer(), definition.colors(), definition.shopHidden(),
                    effects, definition.particle(), definition.revision());
        }, "buff-removed", Locale.text("buffId", effectName));
    }

    private void setTitleParticle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctags setTitleParticle <称号ID> <粒子类型> [粒子id] (颜色) (颜色) (颜色)");
            return;
        }
        String tagId = args[1].toLowerCase(java.util.Locale.ROOT);
        String particleType = args[2].toUpperCase(java.util.Locale.ROOT);
        if (!com.dracave.tags.engine.ParticleEngine.validParticle(particleType)) {
            sender.sendMessage("§c未知粒子类型：" + args[2]);
            return;
        }
        String particleId = args.length >= 4 && !args[3].startsWith("#") ? args[3] : null;
        List<String> colors = new ArrayList<>();
        for (int i = particleId == null ? 3 : 4; i < args.length && colors.size() < 3; i++) {
            String color = args[i].toUpperCase(java.util.Locale.ROOT);
            if (!COLOR.matcher(color).matches()) {
                sender.sendMessage("§c颜色必须是 #RRGGBB：" + args[i]);
                return;
            }
            colors.add(color);
        }
        List<String> finalColors = colors;
        String finalParticleId = particleId;
        modifyDefinition(sender, tagId, definition -> new DCTag(
                definition.id(), definition.display(), definition.description(), definition.icon(),
                definition.order(), definition.defaultUnlocked(), definition.permission(), definition.animation(),
                definition.purchaseOffer(), definition.colors(), definition.shopHidden(),
                definition.potionEffects(), new DCTagPart(particleType, finalParticleId, finalColors), definition.revision()),
                "particle-set", Locale.text("id", tagId));
    }

    private void removeTitleParticle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e用法：/dctags removeTitleParticle <称号ID>");
            return;
        }
        String tagId = args[1].toLowerCase(java.util.Locale.ROOT);
        modifyDefinition(sender, tagId, definition -> new DCTag(
                definition.id(), definition.display(), definition.description(), definition.icon(),
                definition.order(), definition.defaultUnlocked(), definition.permission(), definition.animation(),
                definition.purchaseOffer(), definition.colors(), definition.shopHidden(),
                definition.potionEffects(), null, definition.revision()),
                "particle-removed", Locale.text("id", tagId));
    }

    private void modifyDefinition(CommandSender sender, String tagId,
                                  java.util.function.Function<DCTag, DCTag> transform,
                                  String messageKey, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        DCTag current = plugin.registry().get(tagId);
        if (current == null) {
            plugin.messages().send(sender, "unknown-title", Locale.text("id", tagId));
            return;
        }
        DCTag changed;
        try {
            changed = transform.apply(current);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§c" + ex.getMessage());
            return;
        }
        plugin.defEngine().update(changed, current.revision()).thenAccept(saved ->
                SchedulerUtil.runTask(plugin, () -> {
                    if (saved) {
                        plugin.messages().send(sender, messageKey, resolvers);
                    } else {
                        plugin.messages().send(sender, "custom-result-conflict");
                    }
                }));
    }

    private void reload(CommandSender sender) {
        plugin.reloadFiles();
        plugin.defEngine().reload().thenRun(() -> {
            plugin.messages().send(sender, "reloaded");
        });
    }

    private void set(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctags set <玩家名> <称号ID> [天数]（0 为永久）");
            return;
        }
        String playerName = args[1];
        String tagId = args[2].toLowerCase(java.util.Locale.ROOT);
        int days = args.length >= 4 ? parseInt(args[3], 0) : 0;
        grantToPlayer(sender, playerName, tagId, days, true);
    }

    private void addPlayerTitle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctags addPlayerTitle <玩家名> <称号名称或ID> [天数]");
            return;
        }
        String playerName = args[1];
        String tagId = resolveTagId(args[2]);
        int days = args.length >= 4 ? parseInt(args[3], 0) : 0;
        grantToPlayer(sender, playerName, tagId, days, false);
    }

    private void grantToPlayer(CommandSender sender, String playerName, String tagId, int days, boolean equipNow) {
        if (plugin.registry().get(tagId) == null) {
            plugin.messages().send(sender, "unknown-title", Locale.text("id", tagId));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && Bukkit.getPlayerExact(playerName) == null) {
            plugin.messages().send(sender, "unknown-player", Locale.text("player", playerName));
            return;
        }
        UUID targetId = target.getUniqueId();
        plugin.tagEngine().grant(targetId, tagId, days, equipNow).thenAccept(result ->
                SchedulerUtil.runTask(plugin, () -> {
                    if (result != DCTagResult.SUCCESS) {
                        plugin.messages().send(sender, "operation-failed");
                        return;
                    }
                    plugin.messages().send(sender, equipNow ? "title-set" : "title-granted",
                            Locale.text("player", playerName),
                            Locale.parsed("title", renderedTag(tagId)),
                            Locale.text("days", days > 0 ? Integer.toString(days) : "永久"));
                    if (equipNow) {
                        plugin.tagEngine().equip(targetId, tagId);
                    }
                }));
    }

    private void addCoin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctags addCoin <玩家名> <金额>");
            return;
        }
        String playerName = args[1];
        long amount = parseLong(args[2]);
        if (amount <= 0) {
            sender.sendMessage("§c金额必须大于 0。");
            return;
        }
        UUID targetId = resolvePlayerId(playerName, sender);
        if (targetId == null) {
            return;
        }
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                plugin.coinStore().add(targetId, amount);
                long balance = plugin.coinStore().balance(targetId);
                plugin.messages().send(sender, "coin-added", Locale.text("player", playerName),
                        Locale.text("amount", Long.toString(amount)),
                        Locale.parsed("currency", coinDisplay()),
                        Locale.text("balance", Long.toString(balance)));
            } catch (Exception ex) {
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }

    private void subtractCoin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctags subtractCoin <玩家名> <金额>");
            return;
        }
        String playerName = args[1];
        long amount = parseLong(args[2]);
        if (amount <= 0) {
            sender.sendMessage("§c金额必须大于 0。");
            return;
        }
        UUID targetId = resolvePlayerId(playerName, sender);
        if (targetId == null) {
            return;
        }
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                if (!plugin.coinStore().subtract(targetId, amount)) {
                    plugin.messages().send(sender, "coin-insufficient", Locale.text("player", playerName),
                            Locale.parsed("currency", coinDisplay()));
                    return;
                }
                long balance = plugin.coinStore().balance(targetId);
                plugin.messages().send(sender, "coin-subtracted", Locale.text("player", playerName),
                        Locale.text("amount", Long.toString(amount)),
                        Locale.parsed("currency", coinDisplay()),
                        Locale.text("balance", Long.toString(balance)));
            } catch (Exception ex) {
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }

    private String coinDisplay() {
        return plugin.getConfig().getString("shop.currencies.coin.display", "称号币");
    }

    private void changeItem(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§e用法：/dctags changeItem <称号ID> <天数> <数量> [玩家名]（将玩家持有的称号转换为物品卡）");
            return;
        }
        String tagId = args[1].toLowerCase(java.util.Locale.ROOT);
        int days = parseInt(args[2], 0);
        int count = parseInt(args[3], 1);
        if (count < 1 || count > 64) {
            sender.sendMessage("§c数量必须为 1-64。");
            return;
        }
        final String playerName;
        final Player onlinePlayer;
        if (args.length >= 5) {
            playerName = args[4];
            onlinePlayer = null;
        } else {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage("§c请指定玩家名。");
                return;
            }
            onlinePlayer = playerSender;
            playerName = playerSender.getName();
        }
        UUID targetId = resolvePlayerId(playerName, sender);
        if (targetId == null) {
            return;
        }
        PlayerData data = plugin.tagEngine().getCached(targetId);
        if (data == null || !data.unlocked().contains(tagId)) {
            plugin.messages().send(sender, "not-unlocked");
            return;
        }
        String finalPlayerName = playerName;
        plugin.tagEngine().revoke(targetId, tagId).thenAccept(result ->
                SchedulerUtil.runTask(plugin, () -> {
                    if (result != DCTagResult.SUCCESS) {
                        plugin.messages().send(sender, "operation-failed");
                        return;
                    }
                    Player target = onlinePlayer != null ? onlinePlayer : Bukkit.getPlayerExact(finalPlayerName);
                    if (target == null || !target.isOnline()) {
                        sender.sendMessage("§e玩家不在线，称号已收回，请上线后再领取物品。");
                        return;
                    }
                    for (int i = 0; i < count; i++) {
                        target.getInventory().addItem(plugin.cardEngine().tagCard(tagId, days));
                    }
                    sender.sendMessage("§a已将 " + finalPlayerName + " 的称号 §f" + tagId + " §a转换为 " + count + " 张称号卡。");
                }));
    }

    private void addReward(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§e用法：/dctags addReward <称号数量> <类型 vault|playerpoints|coin> <金额>");
            return;
        }
        int number = parseInt(args[1], 0);
        if (number < 1) {
            sender.sendMessage("§c称号数量必须大于 0。");
            return;
        }
        RewardKind type;
        try {
            type = RewardKind.parse(args[2]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§c未知奖励类型：" + args[2]);
            return;
        }
        long amount = parseLong(args[3]);
        if (amount <= 0) {
            sender.sendMessage("§c金额必须大于 0。");
            return;
        }
        long id = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        RewardCfg reward = new RewardCfg(id, number, type, amount);
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                plugin.rewardStore().add(reward);
                plugin.messages().send(sender, "reward-configured",
                        Locale.text("number", Integer.toString(number)),
                        Locale.text("amount", Long.toString(amount)),
                        Locale.text("type", type.id()));
            } catch (Exception ex) {
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }

    private void randomCard(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctags randomCard <货币类型 vault|playerpoints|coin|item> <天数>（0 为永久）");
            return;
        }
        EcoType currency;
        try {
            currency = EcoType.parse(args[1]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§c未知货币类型：" + args[1]);
            return;
        }
        int days = parseInt(args[2], 0);
        player.getInventory().addItem(plugin.cardEngine().randomCard(currency, days));
        plugin.messages().send(player, "card-given", Locale.text("type", currency.id()), Locale.text("days", Integer.toString(days)));
    }

    private void setCustom(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctags setCustom <玩家名> <次数>");
            return;
        }
        int quota = parseInt(args[2], -1);
        if (quota < 0) {
            sender.sendMessage("§c次数必须大于等于 0。");
            return;
        }
        UUID targetId = resolvePlayerId(args[1], sender);
        if (targetId == null) {
            return;
        }
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                plugin.quotaStore().setQuota(targetId, quota);
                plugin.messages().send(sender, "quota-set", Locale.text("player", args[1]),
                        Locale.text("quota", quota == 0 ? "无上限（由权限决定）" : Integer.toString(quota)));
            } catch (Exception ex) {
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }

    private void addCustom(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e用法：/dctags addCustom <玩家名> <次数>");
            return;
        }
        int quota = parseInt(args[2], 0);
        if (quota <= 0) {
            sender.sendMessage("§c次数必须大于 0。");
            return;
        }
        UUID targetId = resolvePlayerId(args[1], sender);
        if (targetId == null) {
            return;
        }
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                plugin.quotaStore().addQuota(targetId, quota);
                plugin.messages().send(sender, "quota-added", Locale.text("player", args[1]),
                        Locale.text("quota", Integer.toString(quota)));
            } catch (Exception ex) {
                plugin.messages().send(sender, "operation-failed");
            }
        });
    }

    private void upload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dracave.tags.admin.upload")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        String mode = "data";
        boolean checkOnly = false;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--check")) {
                checkOnly = true;
            } else if (args[i].equalsIgnoreCase("all")) {
                mode = "all";
            } else if (args[i].equalsIgnoreCase("data")) {
                mode = "data";
            }
        }
        if ("all".equals(mode)) {
            final boolean check = checkOnly;
            var future = check ? plugin.defEngine().checkUpload() : plugin.defEngine().upload();
            future.thenAccept(result -> SchedulerUtil.runTask(plugin, () -> {
                if (result.valid()) {
                    if (check) {
                        sender.sendMessage("§a校验通过，共 " + result.count() + " 个称号。");
                    } else {
                        sender.sendMessage("§a上传完成：新增 " + result.inserted() + " 个，更新 " + result.updated() + " 个，已拆分为 tags/ 文件。");
                    }
                } else {
                    sender.sendMessage("§c上传/校验失败：");
                    for (String error : result.errors()) {
                        sender.sendMessage("§c- " + error);
                    }
                }
            }));
        } else {
            plugin.defEngine().sync().thenAccept(result -> SchedulerUtil.runTask(plugin, () -> {
                if (result.valid()) {
                    sender.sendMessage("§a同步完成，已从数据库加载 " + result.count() + " 个称号，已覆写 tags.yml 和 tags/ 文件。");
                } else {
                    sender.sendMessage("§c同步失败：");
                    for (String error : result.errors()) {
                        sender.sendMessage("§c- " + error);
                    }
                }
            }));
        }
    }

    private void panel(CommandSender sender, String[] args, boolean byId) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("dracave.tags.admin.panel")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        List<DCTag> tags = plugin.registry().configured();
        if (tags.isEmpty()) {
            plugin.messages().send(player, "gui-no-title-data");
            return;
        }
        DCTag target;
        if (args.length < 2) {
            target = tags.get(0);
        } else if (byId) {
            target = plugin.registry().get(args[1].toLowerCase(java.util.Locale.ROOT));
        } else {
            String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            target = tags.stream()
                    .filter(t -> DCTagRenderer.plain(t, System.currentTimeMillis()).equals(text))
                    .findFirst().orElse(tags.stream()
                            .filter(t -> t.display().equals(text))
                            .findFirst().orElse(null));
        }
        if (target == null) {
            plugin.messages().send(player, "unknown-title", Locale.text("id", String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
            return;
        }
        plugin.adminConsole().openEditor(player, target.id(), AdminConsole.EditorReturn.COMMAND, 0);
    }

    private void panelEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("dracave.tags.admin.panel")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (args.length < 3 || !plugin.adminConsole().ownsDraft(player, args[1])) {
            sender.sendMessage("§e用法：/dctags panel-edit <称号ID> <操作> <参数...>（先 /dctags panel-id <ID> 打开面板）");
            return;
        }
        String tagId = args[1].toLowerCase(java.util.Locale.ROOT);
        String operation = args[2].toLowerCase(java.util.Locale.ROOT);
        switch (operation) {
            case "text" -> {
                if (args.length < 4) {
                    sender.sendMessage("§e用法：panel-edit <ID> text <新文本>");
                    return;
                }
                String text = mini.escapeTags(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                if (text.codePointCount(0, text.length()) > 64) {
                    sender.sendMessage("§c文本过长。");
                    return;
                }
                modifyDefinition(sender, tagId, definition -> new DCTag(definition.id(), text,
                        definition.description(), definition.icon(), definition.order(), definition.defaultUnlocked(),
                        definition.permission(), definition.animation(), definition.purchaseOffer(), definition.colors(),
                        definition.shopHidden(), definition.potionEffects(), definition.particle(), definition.revision()),
                        "panel-updated");
            }
            case "price" -> {
                if (args.length < 4) {
                    sender.sendMessage("§e用法：panel-edit <ID> price <金额|none>");
                    return;
                }
                String priceValue = args[3];
                modifyDefinition(sender, tagId, definition -> {
                    if (priceValue.equalsIgnoreCase("none")) {
                        return new DCTag(definition.id(), definition.display(), definition.description(),
                                definition.icon(), definition.order(), definition.defaultUnlocked(), definition.permission(),
                                definition.animation(), null, definition.colors(), definition.shopHidden(),
                                definition.potionEffects(), definition.particle(), definition.revision());
                    }
                    return new DCTag(definition.id(), definition.display(), definition.description(),
                            definition.icon(), definition.order(), definition.defaultUnlocked(), definition.permission(),
                            definition.animation(), new DCTagOffer(
                            definition.purchaseOffer() == null ? EcoType.VAULT : definition.purchaseOffer().currency(),
                            new BigDecimal(priceValue)), definition.colors(), definition.shopHidden(),
                            definition.potionEffects(), definition.particle(), definition.revision());
                }, "panel-updated");
            }
            default -> sender.sendMessage("§e支持的操作：text <文本> / price <金额|none>；其他请使用管理面板 GUI。");
        }
    }

    private void help(CommandSender sender) {
        boolean admin = sender.hasPermission("dracave.tags.admin");
        sender.sendMessage("§e§m-------------§f[§eDraCaveTags§f]§e§m-------------");
        sender.sendMessage("§e/dctags open §f打开称号仓库");
        sender.sendMessage("§e/dctags shop §f打开称号商店");
        sender.sendMessage("§e/dctags custom [称号名称] §f自定义称号");
        sender.sendMessage("§e/dctags wear <ID|none> §f穿戴/卸下称号");
        sender.sendMessage("§e/dctags clear §f卸下当前称号");
        sender.sendMessage("§e/dctags view [类型] (玩家名) §f查看称号列表");
        sender.sendMessage("§e/dctags reward §f奖励中心");
        sender.sendMessage("§e/dctags ranking §f称号数量排行榜");
        if (admin) {
            sender.sendMessage("§e/dctags adminShop §f管理称号商店");
            sender.sendMessage("§e/dctags add <货币> <名称> <价格> [天数] [隐藏] [玩家名] §f创建称号");
            sender.sendMessage("§e/dctags set <玩家> <ID> [天数] §f设置并穿戴称号");
            sender.sendMessage("§e/dctags addPlayerTitle <玩家> <称号名称或ID> [天数] §f发放称号");
            sender.sendMessage("§e/dctags del <ID> §f删除称号");
            sender.sendMessage("§e/dctags setDescription <ID> <描述> §f设置描述");
            sender.sendMessage("§e/dctags addPermission <称号名称或ID> <权限> §f设置购买权限");
            sender.sendMessage("§e/dctags setTitleBuff <ID> POTION_EFFECT <效果> [等级] §f添加药水加成");
            sender.sendMessage("§e/dctags delBuff <ID> <效果> §f删除药水加成");
            sender.sendMessage("§e/dctags setTitleParticle <ID> <粒子> [id] (颜色)… §f设置粒子");
            sender.sendMessage("§e/dctags removeTitleParticle <ID> §f移除粒子");
            sender.sendMessage("§e/dctags addCoin <玩家> <金额> §f增加称号币");
            sender.sendMessage("§e/dctags subtractCoin <玩家> <金额> §f扣除称号币");
            sender.sendMessage("§e/dctags changeItem <ID> <天数> <数量> [玩家] §f称号转物品卡");
            sender.sendMessage("§e/dctags addReward <数量> <类型> <金额> §f配置里程碑奖励");
            sender.sendMessage("§e/dctags randomCard <货币> <天数> §f生成随机称号卡");
            sender.sendMessage("§e/dctags setCustom <玩家> <次数> §f设置自定义额度");
            sender.sendMessage("§e/dctags addCustom <玩家> <次数> §f追加自定义额度");
            sender.sendMessage("§e/dctags panel-id <ID> §f打开编辑面板");
            sender.sendMessage("§e/dctags panel <称号文本> §f按名称打开编辑面板");
            sender.sendMessage("§e/dctags panel-edit <ID> <操作> <参数> §f命令行编辑称号");
            sender.sendMessage("§e/dctags upload [data|all] [--check] §f同步数据库→tags.yml+tags(data)或上传tags.yml→数据库+tags(all)");
            sender.sendMessage("§e/dctags reload §f重载配置");
            sender.sendMessage("§e/dctags listTitle §f列出全部称号");
        }
    }

    private void requirePlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (sender instanceof Player player) {
            action.accept(player);
        } else {
            plugin.messages().send(sender, "player-only");
        }
    }

    private String resolveTagId(String value) {
        if (plugin.registry().get(value) != null) {
            return value.toLowerCase(java.util.Locale.ROOT);
        }
        return plugin.registry().all().stream()
                .filter(t -> t.display().equals(value) || DCTagRenderer.plain(t, System.currentTimeMillis()).equals(value))
                .map(DCTag::id)
                .findFirst()
                .orElse(value.toLowerCase(java.util.Locale.ROOT));
    }

    private UUID resolvePlayerId(String playerName, CommandSender sender) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        if (!offline.hasPlayedBefore()) {
            plugin.messages().send(sender, "unknown-player", Locale.text("player", playerName));
            return null;
        }
        return offline.getUniqueId();
    }

    private String renderedTag(String tagId) {
        DCTag tag = plugin.registry().get(tagId);
        return tag == null ? tagId : DCTagRenderer.miniMessage(tag, System.currentTimeMillis());
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("open", "shop", "custom", "wear", "clear", "view", "reward", "ranking"));
            if (sender.hasPermission("dracave.tags.admin")) {
                values.addAll(List.of("listTitle", "adminShop", "add", "create", "del", "set", "addPlayerTitle", "addCoin",
                        "subtractCoin", "changeItem", "addReward", "randomCard", "setCustom", "addCustom",
                        "setDescription", "addPermission", "setTitleBuff", "delBuff", "setTitleParticle",
                        "removeTitleParticle", "reload", "panel", "panel-id", "panel-edit", "upload"));
            }
            return filter(values, args[0]);
        }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "custom" -> {
                if (args.length == 2) {
                    return filter(List.of("create", "edit", "delete"), args[1]);
                }
                String action = args[1].toLowerCase(java.util.Locale.ROOT);
                if ((action.equals("create") || action.equals("edit")) && args.length == (action.equals("edit") ? 4 : 3)) {
                    return filter(customTypes(sender), args[args.length - 1]);
                }
                return List.of();
            }
            case "wear" -> {
                if (args.length == 2 && sender instanceof Player player) {
                    PlayerData data = plugin.tagEngine() == null ? null : plugin.tagEngine().getCached(player.getUniqueId());
                    if (data == null) {
                        return List.of();
                    }
                    List<String> values = new ArrayList<>(data.unlocked());
                    values.add("none");
                    return filter(values, args[1]);
                }
                return List.of();
            }
            case "create" -> {
                if (args.length == 2) {
                    return filter(List.of("[称号文本]"), args[1]);
                }
                if (args.length == 3) {
                    return filter(List.of("#", "#FF0000,#0000FF"), args[2]);
                }
                if (args.length == 4) {
                    return filter(List.of("vault", "coin", "point", "item"), args[3]);
                }
                if (args.length == 5) {
                    return filter(List.of("1000", "5000", "10000"), args[4]);
                }
                if (args.length == 6) {
                    java.util.List<String> items = new java.util.ArrayList<>(
                    java.util.Arrays.stream(org.bukkit.Material.values()).filter(org.bukkit.Material::isItem)
                            .map(m -> "minecraft:" + m.name().toLowerCase(java.util.Locale.ROOT)).toList());
                    items.addAll(ItemResolver.allItemIds());
                    return filter(items, args[5]);
                }
                return List.of();
            }
            case "add" -> {
                if (args.length == 2) {
                    return filter(List.of("vault", "playerpoints", "coin", "item"), args[1]);
                }
                if (args.length == 3) {
                    return filter(List.of("[称号名称]"), args[2]);
                }
                if (args.length == 4) {
                    return filter(List.of("1000", "5000", "10000"), args[3]);
                }
                if (args.length == 5) {
                    return filter(List.of("0", "7", "30", "365"), args[4]);
                }
                if (args.length == 6) {
                    return filter(List.of("true", "false"), args[5]);
                }
                if (args.length == 7) {
                    return filter(onlinePlayers(), args[6]);
                }
                return List.of();
            }
            case "del", "set", "addplayertitle", "setdescription", "addpermission", "settitlebuff", "delbuff",
                    "settitleparticle", "removetitleparticle", "changeitem" -> {
                if (args.length == 2) {
                    if (sub.equals("set") || sub.equals("addplayertitle")) {
                        return filter(onlinePlayers(), args[1]);
                    }
                    if (sub.equals("addpermission")) {
                        return filter(tagIdsAndNames(), args[1]);
                    }
                    return filter(tagIds(), args[1]);
                }
                if (args.length == 3 && (sub.equals("set") || sub.equals("addplayertitle"))) {
                    if (sub.equals("addplayertitle")) {
                        return filter(tagIdsAndNames(), args[2]);
                    }
                    return filter(tagIds(), args[2]);
                }
                if (args.length == 3 && (sub.equals("setdescription") || sub.equals("addpermission"))) {
                    return filter(List.of("[内容]"), args[2]);
                }
                if (args.length == 3 && sub.equals("settitlebuff")) {
                    return filter(List.of("POTION_EFFECT"), args[2]);
                }
                if (args.length == 4 && sub.equals("settitlebuff")) {
                    return filter(potionNames(), args[3]);
                }
                if (args.length == 3 && sub.equals("changeitem")) {
                    return filter(List.of("0", "7", "30"), args[2]);
                }
                if (args.length == 4 && sub.equals("changeitem")) {
                    return filter(List.of("1", "5", "10"), args[3]);
                }
                return List.of();
            }
            case "setcustom", "addcustom", "addcoin", "subtractcoin" -> {
                if (args.length == 2) {
                    return filter(onlinePlayers(), args[1]);
                }
                if (args.length == 3 && (sub.equals("setcustom") || sub.equals("addcustom"))) {
                    return filter(List.of("1", "5", "10"), args[2]);
                }
                if (args.length == 3 && (sub.equals("addcoin") || sub.equals("subtractcoin"))) {
                    return filter(List.of("1000", "5000", "10000"), args[2]);
                }
                return List.of();
            }
            case "randomcard", "addreward" -> {
                if (args.length == 2) {
                    return filter(List.of("vault", "playerpoints", "coin", "item"), args[1]);
                }
                if (args.length == 3) {
                    return filter(List.of("0", "7", "30", "365"), args[2]);
                }
                return List.of();
            }
            case "upload" -> {
                if (args.length == 2) {
                    return filter(List.of("data", "all", "--check"), args[1]);
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("all")) {
                    return filter(List.of("--check"), args[2]);
                }
                return List.of();
            }
            case "view" -> {
                if (args.length == 2) {
                    return filter(List.of("shop", "reward"), args[1]);
                }
                if (args.length == 3) {
                    return filter(onlinePlayers(), args[2]);
                }
                return List.of();
            }
            case "panel" -> {
                if (args.length == 2) {
                    return filter(plugin.registry().configured().stream()
                            .map(tag -> DCTagRenderer.plain(tag, System.currentTimeMillis())).distinct().toList(), args[1]);
                }
                return List.of();
            }
            case "panel-id", "panel-edit" -> {
                if (args.length == 2) {
                    return filter(tagIds(), args[1]);
                }
                if (sub.equals("panel-edit") && args.length == 3) {
                    return filter(List.of("text", "price"), args[2]);
                }
                return List.of();
            }
            default -> {
            }
        }
        return List.of();
    }

    private List<String> customTypes(CommandSender sender) {
        List<String> types = new ArrayList<>();
        if (senderHasPermission(sender, "dracave.tags.custom.static")) {
            types.add("static");
        }
        if (senderHasPermission(sender, "dracave.tags.custom.dynamic")) {
            types.addAll(List.of("gradient", "rainbow", "flash", "frames"));
        }
        return types.isEmpty() ? List.of("static", "gradient", "rainbow", "flash", "frames") : types;
    }

    private boolean senderHasPermission(CommandSender sender, String permission) {
        return sender == null || sender.isOp() || sender.hasPermission(permission);
    }

    private List<String> potionNames() {
        return Arrays.stream(PotionEffectType.values())
                .filter(java.util.Objects::nonNull)
                .map(PotionEffectType::getName)
                .sorted()
                .toList();
    }

    private List<String> tagIds() {
        List<String> ids = plugin.registry().all().stream().map(DCTag::id).toList();
        return ids.isEmpty() ? List.of("source-born") : ids;
    }

    private List<String> tagIdsAndNames() {
        List<String> result = new ArrayList<>();
        for (DCTag tag : plugin.registry().all()) {
            result.add(tag.id());
            result.add(tag.display());
            result.add(DCTagRenderer.plain(tag, System.currentTimeMillis()));
        }
        return new ArrayList<>(new LinkedHashSet<>(result));
    }

    private List<String> onlinePlayers() {
        List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return players.isEmpty() ? List.of("玩家名") : players;
    }

    private static List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(java.util.Locale.ROOT);
        return new LinkedHashSet<>(values).stream()
                .filter(value -> value.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                .sorted()
                .toList();
    }
}
