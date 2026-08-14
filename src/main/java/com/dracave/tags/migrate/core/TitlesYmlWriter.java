package com.dracave.tags.migrate.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TitlesYmlWriter {
    private TitlesYmlWriter() {
    }

    public static void write(Path file, List<TitleData.SourceTitle> titles,
                             Map<Long, String> idMap, List<TitleData.TitleBuff> buffs,
                             MigrateConfig.Mode mode) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (mode == MigrateConfig.Mode.STATIC) {
            sb.append("# DraCaveTags 称号定义（/ttt title null 生成，静态 MiniMessage）\n");
            sb.append("# text 为 MiniMessage 标签（粗体/乱码/静态渐变），执行 /dctags upload 导入即静态显示。\n\n");
        } else {
            sb.append("# DraCaveTags 称号定义（/ttt title color 生成，动态渐变）\n");
            sb.append("# 执行 /dctags upload 导入；多色称号为流动渐变。\n\n");
        }
        sb.append("tags:\n");
        for (TitleData.SourceTitle t : titles) {
            String id = idMap.get(t.id());
            if (id == null) {
                continue;
            }
            LegacyParser.ParsedTitle parsed = LegacyParser.parse(t.titleName());
            String text;
            List<String> colors;
            if (mode == MigrateConfig.Mode.STATIC) {
                text = LegacyParser.toMiniMessage(t.titleName());
                colors = List.of();
            } else {
                text = parsed.text();
                colors = parsed.colors();
                if (colors.isEmpty()) {
                    colors = List.of("#FFFFFF");
                }
            }
            sb.append("  \"").append(id).append("\":\n");
            sb.append("    text: ").append(yaml(text)).append('\n');
            if (mode != MigrateConfig.Mode.STATIC) {
                if (colors.size() >= 2) {
                    sb.append("    animation-type: gradient\n");
                    sb.append("    gradient-cycle-seconds: 2.0\n");
                }
                sb.append("    colors: [");
                for (int i = 0; i < colors.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append('"').append(colors.get(i)).append('"');
                }
                sb.append("]\n");
            }
            String icon = t.icon() != null && !t.icon().isBlank() ? t.icon() : "NAME_TAG";
            sb.append("    icon: ").append(icon).append('\n');
            sb.append("    order: ").append(t.id()).append('\n');
            sb.append("    default-unlocked: false\n");
            if (t.description() != null && !t.description().isBlank()) {
                sb.append("    description:\n      - ").append(yaml(t.description())).append('\n');
            }
            boolean hasBuff = false;
            java.util.Set<String> seenBuffs = new java.util.HashSet<>();
            for (TitleData.TitleBuff buff : buffs) {
                if (buff.titleId() == t.id()) {
                    if (!seenBuffs.add(buff.potionName())) {
                        continue;
                    }
                    if (!hasBuff) {
                        sb.append("    potion-effects:\n");
                        hasBuff = true;
                    }
                    sb.append("      - type: ").append(buff.potionName()).append('\n');
                    sb.append("        level: ").append(buff.potionLevel()).append('\n');
                }
            }
            boolean hidden = t.isHide() || "not".equals(t.buyType()) || "activity".equals(t.buyType())
                    || "permission".equals(t.buyType());
            String currency = null;
            BigDecimal price = null;
            String item = null;
            switch (t.buyType()) {
                case "vault" -> {
                    currency = "vault";
                    price = BigDecimal.valueOf(t.amount());
                }
                case "playerpoints" -> {
                    currency = "playerpoints";
                    price = BigDecimal.valueOf(t.amount());
                }
                case "coin" -> {
                    currency = "coin";
                    price = BigDecimal.valueOf(t.amount());
                }
                case "itemstack" -> {
                    currency = "item";
                    price = BigDecimal.valueOf(t.amount());
                    item = parseItemMaterial(t.itemStack());
                }
                default -> {
                }
            }
            if (hidden) {
                currency = null;
                price = null;
                item = null;
            }
            if (currency != null && price != null && price.signum() <= 0) {
                currency = null;
                price = null;
                item = null;
            }
            sb.append("    shop:\n");
            sb.append("      hidden: ").append(hidden).append('\n');
            if (currency != null) {
                sb.append("      currency: ").append(currency).append('\n');
                sb.append("      price: \"").append(price).append("\"\n");
                if (item != null) {
                    sb.append("      item: ").append(item).append('\n');
                }
            }
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    static String parseItemMaterial(String itemStackYaml) {
        if (itemStackYaml == null || itemStackYaml.isBlank()) {
            return null;
        }
        for (String line : itemStackYaml.split("\n")) {
            String t = line.trim();
            if (t.startsWith("id:")) {
                String id = t.substring(3).trim();
                if (id.startsWith("minecraft:")) {
                    id = id.substring("minecraft:".length());
                }
                String material = id.toUpperCase(Locale.ROOT).replace(' ', '_');
                return material.isEmpty() ? null : material;
            }
            if (t.startsWith("type:")) {
                String material = t.substring(5).trim().toUpperCase(Locale.ROOT);
                return material.isEmpty() ? null : material;
            }
        }
        return null;
    }

    private static String yaml(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "''") + "'";
    }
}
