package com.dracave.tags.listen;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.handlers.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class DCTagListener implements Listener {
    private final DraCaveTags plugin;

    public DCTagListener(DraCaveTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (plugin.tagEngine() == null) {
            return;
        }
        plugin.tagEngine().load(event.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.tagEngine() == null) {
            return;
        }
        plugin.tagEngine().load(player.getUniqueId()).thenAccept(data -> {
            if (data == null) {
                return;
            }
            plugin.tagEngine().reconcileEffects(player.getUniqueId());
            plugin.tagEngine().purgeExpired(player.getUniqueId());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.buffEngine() != null) {
            plugin.buffEngine().release(player);
        }
        if (plugin.particleEngine() != null) {
            plugin.particleEngine().reconcile(player.getUniqueId());
        }
        if (plugin.tagEngine() != null) {
            plugin.tagEngine().unload(player.getUniqueId());
        }
        if (plugin.chatPrompt() != null) {
            plugin.chatPrompt().clear(player);
        }
    }
}
