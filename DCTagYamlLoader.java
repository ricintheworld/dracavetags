package com.dracave.tags.config;

import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.DCTagAnim;
import com.dracave.tags.handlers.DCTagOffer;
import com.dracave.tags.handlers.DCTagPart;
import com.dracave.tags.handlers.DCTagPotion;
import com.dracave.tags.handlers.EcoType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class DCTagYamlLoader {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Predicate<String> materialValidator;
    private final Predicate<String> effectValidator;

    public DCTagYamlLoader() {
        this(DCTagYamlLoader::validMaterial, type -> PotionEffectType.getByName(type) != null);
    }

    DCTagYamlLoader(Predicate<String> materialValidator, Predicate<String> effectValidator) {
        this.materialValidator = materialValidator;
        this.effectValidator = effectValidator;
    }

    public ParseResult parse(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("tags");
        if (root == null) {
            // 兼容旧版 DraCaveTitle 的 titles.yml 根节点
            root = yaml.getConfigurationSection("titles");
        }
        if (root == null) {
            return new ParseResult(List.of(), List.of("缺少 tags 根节点"));
        }
        List<DCTag> definitions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                errors.add(id + ": 配置必须是一个对象");
            } else {
                try {
                    definitions.add(parseDefinition(id, section));
                } catch (RuntimeException ex) {
                    errors.add(id + ": " + ex.getMessage());
                }
            }
        }
        if (definitions.isEmpty() && errors.isEmpty()) {
            errors.add("tags 中没有可上传的称号");
        }
        return new ParseResult(definitions, errors);
    }

    public ParseResult parseSingle(String id, File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try {
            return new ParseResult(List.of(parseDefinition(id, yaml)), List.of());
        } catch (RuntimeException ex) {
            return new ParseResult(List.of(), List.of(file.getName() + ": " + ex.getMessage()));
        }
    }

    private DCTag parseDefinition(String id, ConfigurationSection section) {
        if (!VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("ID 只能使用小写字母、数字、_ 和 -，长度最多 64");
        }
        String text = cleanText(section.getString("text"));
        boolean hasTags = text.indexOf('<') >= 0 || text.indexOf('>') >= 0;
        String icon = section.getString("icon", "NAME_TAG").toUpperCase(Locale.ROOT);
        if (!materialValidator.test(icon)) {
            throw new IllegalArgumentException("icon 不是有效物品: " + icon);
        }
        List<String> colors = new ArrayList<>();
        for (String value : section.getStringList("colors")) {
            String color = value.toUpperCase(Locale.ROOT);
            if (!COLOR.matcher(color).matches()) {
                throw new IllegalArgumentException("颜色必须是 #RRGGBB: " + value);
            }
            colors.add(color);
        }
        if (colors.size() == 1 && section.contains("gradient-cycle-seconds")) {
            throw new IllegalArgumentException("单色称号不支持 gradient-cycle-seconds");
        }
        DCTagAnim animation = parseAnimation(section, colors);
        boolean defaultUnlocked = section.getBoolean("default-unlocked", false);
        ConfigurationSection shop = section.getConfigurationSection("shop");
        boolean hidden = shop == null || shop.getBoolean("hidden", true);
        DCTagOffer offer = parseOffer(shop);
        if (defaultUnlocked && offer != null) {
            throw new IllegalArgumentException("默认解锁称号不能同时配置购买价格");
        }
        List<String> description = section.getStringList("description");
        if (description.size() > 64) {
            throw new IllegalArgumentException("description 最多包含 64 行");
        }
        for (String line : description) {
            if (line.getBytes(StandardCharsets.UTF_8).length > 65535) {
                throw new IllegalArgumentException("description 单行超过 MySQL TEXT 容量");
            }
        }
        String permission = section.getString("permission", "");
        if (permission.length() > 255) {
            throw new IllegalArgumentException("permission 长度不能超过 255");
        }
        if (hasTags && animation != null) {
            throw new IllegalArgumentException("text 含 MiniMessage 标签时只能静态显示，不能配置动画");
        }
        return new DCTag(id, hasTags ? text : MINI.escapeTags(text), description, icon,
                section.getInt("order", 0), defaultUnlocked, permission, animation, offer,
                colors, hidden, parseEffects(section), parseParticle(section), 0);
    }

    private DCTagAnim parseAnimation(ConfigurationSection section, List<String> colors) {
        String type = section.getString("animation-type", "").toLowerCase(Locale.ROOT);
        int periodTicks = section.getInt("period-ticks", -1);
        DCTagAnim.GradientStyle style = DCTagAnim.GradientStyle.parse(section.getString("gradient-mode", ""));
        if (colors.isEmpty() && type.isBlank()) {
            return null;
        }
        if (colors.size() == 1 && (type.isBlank() || type.equals("static"))) {
            return null;
        }
        double seconds = section.getDouble("gradient-cycle-seconds", 2.0);
        if (!Double.isFinite(seconds) || seconds < 0.2 || seconds > 60.0) {
            throw new IllegalArgumentException("gradient-cycle-seconds 必须在 0.2 至 60.0 秒之间");
        }
        int computedTicks = (int) Math.round(seconds * 20.0);
        if (periodTicks > 0) {
            computedTicks = periodTicks;
        }
        return switch (type) {
            case "rainbow" -> DCTagAnim.rainbow(computedTicks);
            case "flash", "flashing" -> {
                if (colors.size() < 2) {
                    throw new IllegalArgumentException("flash 动画需要至少 2 个颜色");
                }
                yield new DCTagAnim(DCTagAnim.AnimType.FLASHING_COLORS, colors, List.of(), computedTicks);
            }
            case "frames", "frame" -> {
                List<String> frames = section.getStringList("frames");
                if (frames.size() < 2 || frames.size() > 10) {
                    throw new IllegalArgumentException("frames 动画需要 2 至 10 帧");
                }
                yield new DCTagAnim(DCTagAnim.AnimType.TEXT_FRAMES, List.of(),
                        frames.stream().map(MINI::escapeTags).toList(), computedTicks);
            }
            case "", "gradient", "flowing" -> {
                if (colors.size() < 2) {
                    throw new IllegalArgumentException("多色/渐变动画需要至少 2 个颜色");
                }
                yield new DCTagAnim(DCTagAnim.AnimType.FLOWING_GRADIENT, colors, List.of(), computedTicks, style);
            }
            case "solid", "cycle" -> {
                if (colors.size() < 2) {
                    throw new IllegalArgumentException("solid 整体变色动画需要至少 2 个颜色");
                }
                yield new DCTagAnim(DCTagAnim.AnimType.SOLID_GRADIENT, colors, List.of(), computedTicks, style);
            }
            default -> throw new IllegalArgumentException("未知动画类型: " + type);
        };
    }

    private DCTagOffer parseOffer(ConfigurationSection shop) {
        if (shop == null) {
            return null;
        }
        String currency = shop.getString("currency");
        String price = shop.getString("price");
        if ((currency != null && !currency.isBlank()) || (price != null && !price.isBlank())) {
            if (currency == null || currency.isBlank() || price == null || price.isBlank()) {
                throw new IllegalArgumentException("shop.currency 和 shop.price 必须同时配置");
            }
            try {
                BigDecimal amount = new BigDecimal(price);
                EcoType type = EcoType.parse(currency);
                if (type == EcoType.ITEM) {
                    String item = shop.getString("item", "").toUpperCase(Locale.ROOT);
                    if (item.isBlank()) {
                        throw new IllegalArgumentException("物品购买（currency: item）必须配置 shop.item 物品材质");
                    }
                    return new DCTagOffer(EcoType.ITEM, amount, item);
                }
                return new DCTagOffer(type, amount);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("shop.price 不是合法数字: " + price);
            }
        }
        return null;
    }

    private List<DCTagPotion> parseEffects(ConfigurationSection section) {
        List<DCTagPotion> effects = new ArrayList<>();
        Set<String> types = new HashSet<>();
        for (Map<?, ?> entry : section.getMapList("potion-effects")) {
            Object rawType = entry.containsKey("type") ? entry.get("type") : "";
            String type = String.valueOf(rawType).toUpperCase(Locale.ROOT);
            if (!effectValidator.test(type)) {
                throw new IllegalArgumentException("未知药水效果: " + type);
            }
            if (!types.add(type)) {
                throw new IllegalArgumentException("药水效果重复: " + type);
            }
            Object rawLevel = entry.containsKey("level") ? entry.get("level") : 1;
            int level;
            try {
                level = rawLevel instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(rawLevel));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(type + " 的 level 必须是整数");
            }
            effects.add(new DCTagPotion(type, level));
        }
        return List.copyOf(effects);
    }

    private DCTagPart parseParticle(ConfigurationSection section) {
        String particleType = section.getString("particle.type", "");
        if (particleType.isBlank()) {
            return null;
        }
        String particleId = section.getString("particle.id", "");
        List<String> colors = section.getStringList("particle.colors");
        for (String color : colors) {
            if (!COLOR.matcher(color.toUpperCase(Locale.ROOT)).matches()) {
                throw new IllegalArgumentException("粒子颜色必须是 #RRGGBB: " + color);
            }
        }
        return new DCTagPart(particleType.toUpperCase(Locale.ROOT),
                particleId.isBlank() ? null : particleId,
                colors.stream().map(c -> c.toUpperCase(Locale.ROOT)).toList());
    }

    private String cleanText(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("缺少 text");
        }
        String text = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("text 不能为空");
        }
        if (text.matches(".*[§\\p{Cntrl}\\p{Cf}].*")) {
            throw new IllegalArgumentException("text 不能包含 § 颜色代码或控制字符");
        }
        if (text.indexOf('<') >= 0 || text.indexOf('>') >= 0) {
            try {
                var comp = MINI.deserialize(text);
                String visible = PlainTextComponentSerializer.plainText().serialize(comp);
                if (visible.codePointCount(0, visible.length()) > 64) {
                    throw new IllegalArgumentException("text 长度必须为 1 至 64 个字符");
                }
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalArgumentException("text 的 MiniMessage 标签不合法: " + ex.getMessage());
            }
        } else if (text.codePointCount(0, text.length()) > 64) {
            throw new IllegalArgumentException("text 长度必须为 1 至 64 个字符");
        }
        return text;
    }

    private static boolean validMaterial(String name) {
        try {
            Material.valueOf(name);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public record ParseResult(List<DCTag> definitions, List<String> errors) {
        public ParseResult {
            definitions = List.copyOf(definitions);
            errors = List.copyOf(errors);
        }
        public boolean valid() {
            return errors.isEmpty();
        }
    }
}
