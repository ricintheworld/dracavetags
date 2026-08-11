package com.dracave.tags.config;

import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.DCTagAnim;
import com.dracave.tags.handlers.DCTagOffer;
import com.dracave.tags.handlers.DCTagPart;
import com.dracave.tags.handlers.DCTagPotion;
import com.dracave.tags.handlers.EcoType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DCTagYamlWriter {
    public void writeAll(List<DCTag> definitions, File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (DCTag def : definitions) {
            writeDefinition(yaml.createSection("tags." + def.id()), def);
        }
        save(yaml, file, "tags.yml");
    }

    public void writeSingle(DCTag def, File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        writeDefinition(yaml, def);
        save(yaml, file, def.id() + ".yml");
    }

    private void save(YamlConfiguration yaml, File file, String displayName) {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            throw new RuntimeException("保存 " + displayName + " 失败: " + ex.getMessage(), ex);
        }
    }

    private void writeDefinition(ConfigurationSection section, DCTag def) {
        section.set("text", def.display());
        if (!def.description().isEmpty()) {
            section.set("description", def.description());
        }
        section.set("icon", def.icon());
        section.set("order", def.order());
        section.set("default-unlocked", def.defaultUnlocked());
        if (!def.permission().isBlank()) {
            section.set("permission", def.permission());
        }
        if (!def.colors().isEmpty()) {
            section.set("colors", def.colors());
        }
        writeAnimation(section, def.animation());
        writeShop(section, def);
        writePotionEffects(section, def.potionEffects());
        writeParticle(section, def.particle());
    }

    private void writeAnimation(ConfigurationSection section, DCTagAnim anim) {
        if (anim == null) {
            return;
        }
        section.set("animation-type", animationTypeName(anim.type()));
        double seconds = anim.periodTicks() / 20.0;
        if (seconds >= 0.2 && seconds <= 60.0 && Math.abs(seconds - Math.round(seconds * 10.0) / 10.0) < 0.001) {
            section.set("gradient-cycle-seconds", String.format(Locale.ROOT, "%.1f", seconds));
        } else {
            section.set("period-ticks", anim.periodTicks());
        }
        if (anim.style() == DCTagAnim.GradientStyle.PINGPONG) {
            section.set("gradient-mode", "pingpong");
        }
        if (anim.type() == DCTagAnim.AnimType.TEXT_FRAMES && !anim.frames().isEmpty()) {
            section.set("frames", anim.frames());
        }
    }

    private void writeShop(ConfigurationSection section, DCTag def) {
        DCTagOffer offer = def.purchaseOffer();
        if (offer == null && def.shopHidden()) {
            return;
        }
        ConfigurationSection shop = section.createSection("shop");
        shop.set("hidden", def.shopHidden());
        if (offer != null) {
            shop.set("currency", offer.currency().id());
            shop.set("price", offer.price().toPlainString());
            if (offer.currency() == EcoType.ITEM && offer.itemMaterial() != null) {
                shop.set("item", offer.itemMaterial());
            }
        }
    }

    private void writePotionEffects(ConfigurationSection section, List<DCTagPotion> effects) {
        if (effects.isEmpty()) {
            return;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (DCTagPotion effect : effects) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", effect.effectType());
            map.put("level", effect.level());
            list.add(map);
        }
        section.set("potion-effects", list);
    }

    private void writeParticle(ConfigurationSection section, DCTagPart particle) {
        if (particle == null) {
            return;
        }
        section.set("particle.type", particle.particleType());
        if (particle.particleId() != null && !particle.particleId().isBlank()) {
            section.set("particle.id", particle.particleId());
        }
        if (!particle.colors().isEmpty()) {
            section.set("particle.colors", particle.colors());
        }
    }

    private String animationTypeName(DCTagAnim.AnimType type) {
        return switch (type) {
            case FLOWING_GRADIENT -> "gradient";
            case SOLID_GRADIENT -> "solid";
            case TEXT_FRAMES -> "frames";
            case RAINBOW -> "rainbow";
            case FLASHING_COLORS -> "flash";
        };
    }
}
