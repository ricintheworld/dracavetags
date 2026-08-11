package com.dracave.tags.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class CustomDeletedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final UUID ownerId;
    private final String tagId;

    public CustomDeletedEvent(UUID ownerId, String tagId) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.ownerId = ownerId;
        this.tagId = tagId;
    }

    public UUID getOwnerId() { return ownerId; }
    public String getTagId() { return tagId; }

    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
