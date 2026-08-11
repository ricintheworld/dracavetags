package com.dracave.tags.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DCTagEquipEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final String previousId;
    private final String tagId;
    private boolean cancelled;

    public DCTagEquipEvent(Player player, String previousId, String tagId) {
        this.player = player;
        this.previousId = previousId;
        this.tagId = tagId;
    }

    public Player getPlayer() { return player; }
    public String getPreviousId() { return previousId; }
    public String getTagId() { return tagId; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
