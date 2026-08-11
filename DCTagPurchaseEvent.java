package com.dracave.tags.api.event;

import com.dracave.tags.handlers.EcoType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.util.UUID;

public class DCTagPurchaseEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final String tagId;
    private final EcoType currency;
    private final BigDecimal amount;
    private final UUID operationId;
    private boolean cancelled;

    public DCTagPurchaseEvent(Player player, String tagId, EcoType currency, BigDecimal amount, UUID operationId) {
        this.player = player;
        this.tagId = tagId;
        this.currency = currency;
        this.amount = amount;
        this.operationId = operationId;
    }

    public Player getPlayer() { return player; }
    public String getTagId() { return tagId; }
    public EcoType getCurrency() { return currency; }
    public BigDecimal getAmount() { return amount; }
    public UUID getOperationId() { return operationId; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
