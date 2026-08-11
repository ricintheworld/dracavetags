package com.dracave.tags.api.event;

import com.dracave.tags.handlers.CustomDraft;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CustomCreateEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final CustomDraft draft;
    private boolean cancelled;

    public CustomCreateEvent(Player player, CustomDraft draft) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.player = player;
        this.draft = draft;
    }

    public Player getPlayer() { return player; }
    public CustomDraft getDraft() { return draft; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
