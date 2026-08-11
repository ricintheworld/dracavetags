package com.dracave.tags.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Locale {
    private final JavaPlugin plugin;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile ConfigurationSection messages;

    public Locale(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.cache.clear();
        String lang = plugin.getConfig().getString("lang", "zh_cn");
        File langFile = new File(plugin.getDataFolder(), "lang" + File.separator + lang + ".yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang" + File.separator + lang + ".yml", false);
        }
        if (langFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(langFile);
            this.messages = yaml;
        } else {
            this.messages = plugin.getConfig().getConfigurationSection("messages");
        }
        if (this.messages == null) {
            this.messages = plugin.getConfig().getConfigurationSection("messages");
        }
    }

    private String raw(String key) {
        return cache.computeIfAbsent(key, k -> {
            if (messages == null) {
                return "<red>Missing messages node</red>";
            }
            String value = messages.getString(k);
            return value != null ? value : "<red>Missing key " + k + "</red>";
        });
    }

    public String rawString(String key) {
        return raw(key);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        String prefix = raw("prefix");
        String message = raw(key);
        Component component = MiniMessage.miniMessage().deserialize(prefix + message, resolvers);
        sender.sendMessage(component);
    }

    public Component component(String key, TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(raw(key), resolvers);
    }

    public static TagResolver text(String name, String value) {
        return Placeholder.unparsed(name, value == null ? "" : value);
    }

    public static TagResolver parsed(String name, String value) {
        return Placeholder.component(name, MiniMessage.miniMessage().deserialize(value == null ? "" : value));
    }
}
