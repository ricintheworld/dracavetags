package com.dracave.tags.hook;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.api.TagsAPI;
import com.dracave.tags.handlers.DCTag;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class TagsExpansion extends PlaceholderExpansion {
    private final DraCaveTags plugin;

    public TagsExpansion(DraCaveTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "dracavetags";
    }

    @Override
    public @NotNull String getAuthor() {
        return "DraCave";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        DCTag tag = TagsAPI.getEquippedTag(player.getUniqueId()).orElse(null);
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "title" -> tag != null ? TagsAPI.getMiniMessage(player.getUniqueId()) : defaultTitle();
            case "title_v" -> tag != null ? TagsAPI.getLegacyAmpersand(player.getUniqueId()) : defaultLegacy('&');
            case "title_s" -> tag != null ? TagsAPI.getLegacySection(player.getUniqueId()) : defaultLegacy('§');
            case "title_only" -> tag != null ? TagsAPI.getPlainText(player.getUniqueId()) : defaultPlain();
            case "title_id" -> tag == null ? "" : tag.id();
            case "title_yesno" -> Boolean.toString(tag != null);
            case "coin" -> String.valueOf(TagsAPI.getCoinBalance(player.getUniqueId()));
            default -> null;
        };
    }

    private String defaultTitle() {
        String raw = plugin.getConfig().getString("chat.default-title", "");
        if (raw.isEmpty()) return "";
        // 支持 MiniMessage：若已含标签则原样交给渲染器解析，否则保留默认灰色
        if (raw.indexOf('<') >= 0) return raw;
        return "<gray>" + raw + "</gray>";
    }

    private String defaultLegacy(char code) {
        String raw = plugin.getConfig().getString("chat.default-title", "");
        if (raw.isEmpty()) return "";
        return code + "7" + raw;
    }

    private String defaultPlain() {
        return plugin.getConfig().getString("chat.default-title", "");
    }
}
