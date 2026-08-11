package com.dracave.tags.api.event;

import com.dracave.tags.handlers.CustomDCTag;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CustomCreatedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final CustomDCTag tag;

    public CustomCreatedEvent(CustomDCTag tag) {
        super(!org.bukkit.Bukkit.isPrimaryThread());
        this.tag = tag;
    }

    public CustomDCTag getTag() { return tag; }

    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
