package com.dracave.tags.listen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.config.Cfg;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.PlayerData;
import com.dracave.tags.render.DCTagRenderer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class ChatListener implements Listener {
    private static final class Holder {
        static final MiniMessage MINI = MiniMessage.miniMessage();
        static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    }

    private final DraCaveTags plugin;
    private final boolean papi;

    public ChatListener(DraCaveTags plugin) {
        this.plugin = plugin;
        this.papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;
        if (!plugin.getConfig().getBoolean(Cfg.CHAT_ENABLED, false)) return;
        PlayerData data = plugin.tagEngine().getCached(event.getPlayer().getUniqueId());
        DCTag title = data != null && data.equippedId() != null
                ? plugin.registry().get(data.equippedId()) : null;
        String titleText;
        if (title != null) {
            titleText = DCTagRenderer.miniMessage(title, System.currentTimeMillis());
        } else {
            titleText = plugin.getConfig().getString(Cfg.CHAT_DEFAULT_TITLE, "");
        }
        String format = plugin.getConfig().getString(Cfg.CHAT_FORMAT, "{title} {player} \u00bb ");
        format = format.replace("{title}", titleText == null ? "" : titleText).replace("{player}", event.getPlayer().getName());
        if (papi) format = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(event.getPlayer(), format);
        String prefix = Holder.LEGACY.serialize(Holder.MINI.deserialize(format));
        String base = event.getFormat().replaceAll("<[^>]+>", "");
        event.setFormat(prefix + base);
    }
}
