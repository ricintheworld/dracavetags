package com.dracave.tags.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class DCTagRevokeEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final UUID playerId;
    private final String tagId;
    private boolean cancelled;

    public DCTagRevokeEvent(UUID playerId, String tagId) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.playerId = playerId;
        this.tagId = tagId;
    }

    public UUID getPlayerId() { return playerId; }
    public String getTagId() { return tagId; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
