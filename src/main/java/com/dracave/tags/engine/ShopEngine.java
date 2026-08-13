package com.dracave.tags.engine;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.api.ShopResult;
import com.dracave.tags.api.ShopStatus;
import com.dracave.tags.api.event.DCTagPurchaseEvent;
import com.dracave.tags.api.event.DCTagPurchasedEvent;
import com.dracave.tags.api.event.DCTagUnlockEvent;
import com.dracave.tags.config.DCTagRegistry;
import com.dracave.tags.config.Cfg;
import com.dracave.tags.economy.EcoProvider;
import com.dracave.tags.economy.EcoRegistry;
import com.dracave.tags.economy.ItemEco;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.DCTagOffer;
import com.dracave.tags.handlers.PlayerData;
import com.dracave.tags.handlers.PurchaseLog;
import com.dracave.tags.handlers.PurchasePhase;
import com.dracave.tags.storage.PlayerStore;
import com.dracave.tags.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ShopEngine {
    private final DraCaveTags plugin;
    private final DCTagRegistry tags;
    private final DCTagEngine tagEngine;
    private final PlayerStore store;
    private final EcoRegistry currencies;
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Object> chargeLocks = new ConcurrentHashMap<>();
    private final long serviceStartedAt = System.currentTimeMillis();
    private final ExecutorService sqlExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "DraCaveTags-Purchase");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    public ShopEngine(DraCaveTags plugin, DCTagRegistry tags, DCTagEngine tagEngine,
                      PlayerStore store, EcoRegistry currencies) {
        this.plugin = plugin;
        this.tags = tags;
        this.tagEngine = tagEngine;
        this.store = store;
        this.currencies = currencies;
    }

    public CompletableFuture<ShopResult> purchase(UUID playerId, String rawTagId) {
        if (!Bukkit.isPrimaryThread()) {
            CompletableFuture<ShopResult> future = new CompletableFuture<>();
            SchedulerUtil.runTask(plugin, () -> purchase(playerId, rawTagId).whenComplete((result, error) -> {
                if (error == null) {
                    future.complete(result);
                } else {
                    future.completeExceptionally(error);
                }
            }));
            return future;
        }
        String tagId = DCTagRegistry.normalizeId(rawTagId);
        DCTag tag = tags.get(tagId);
        DCTagOffer offer = tag == null ? null : tag.purchaseOffer();
        UUID operationId = UUID.randomUUID();
        if (closed || !plugin.getConfig().getBoolean(Cfg.SHOP_ENABLED, true)) {
            return done(ShopStatus.SERVICE_UNAVAILABLE, operationId, tagId, offer, "disabled");
        }
        if (tag == null) {
            return done(ShopStatus.TITLE_NOT_FOUND, operationId, tagId, null, "unknown tag");
        }
        if (offer == null) {
            return done(ShopStatus.NOT_PURCHASABLE, operationId, tagId, null, "no offer");
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return done(ShopStatus.PLAYER_OFFLINE, operationId, tagId, offer, "offline");
        }
        if (!tag.permission().isEmpty() && !player.hasPermission(tag.permission())) {
            return done(ShopStatus.PERMISSION_DENIED, operationId, tagId, offer, tag.permission());
        }
        PlayerData data = tagEngine.getCached(playerId);
        if (data == null) {
            return done(ShopStatus.SERVICE_UNAVAILABLE, operationId, tagId, offer, "data not loaded");
        }
        if (data.unlocked().contains(tagId)) {
            return done(ShopStatus.ALREADY_UNLOCKED, operationId, tagId, offer, "owned");
        }
        EcoProvider currency = providerFor(offer);
        if (currency == null || !currency.available()) {
            return done(ShopStatus.CURRENCY_UNAVAILABLE, operationId, tagId, offer, "provider unavailable");
        }
        String key = playerId + ":" + tagId;
        if (!active.add(key)) {
            return done(ShopStatus.PURCHASE_IN_PROGRESS, operationId, tagId, offer, "duplicate");
        }
        CompletableFuture<ShopResult> result = fire(new DCTagPurchaseEvent(player, tagId, offer.currency(), offer.price(), operationId))
                .thenCompose(allowed -> allowed
                        ? fire(new DCTagUnlockEvent(player, tagId))
                        : CompletableFuture.completedFuture(false))
                .thenCompose(allowed -> allowed
                        ? executePurchase(playerId, tag, offer, operationId, currency)
                        : done(ShopStatus.CANCELLED, operationId, tagId, offer, "event cancelled"))
                .exceptionally(exception -> {
                    plugin.getLogger().severe("购买异常 " + operationId + ": " + exception.getMessage());
                    return result(ShopStatus.DATABASE_ERROR, operationId, tagId, offer, exception.getMessage());
                });
        return result.whenComplete((ignored, exception) -> active.remove(key));
    }

    private CompletableFuture<ShopResult> executePurchase(UUID playerId, DCTag tag,
                                                          DCTagOffer offer, UUID operationId, EcoProvider currency) {
        String tagId = tag.id();
        return sql(() -> store.reservePurchase(playerId, tagId, operationId, offer.storedCurrency(), offer.price()))
                .thenCompose(reserved -> !reserved
                        ? done(ShopStatus.ALREADY_UNLOCKED, operationId, tagId, offer, "already reserved or purchased")
                        : sql(() -> store.transitionPurchase(operationId, PurchasePhase.PENDING, PurchasePhase.CHARGING, null))
                        .thenCompose(marked -> marked
                                ? main(() -> charge(currency, playerId, offer.price()))
                                : CompletableFuture.completedFuture(ChargeResult.FAILED))
                        .thenCompose(charge -> {
                            if (charge == ChargeResult.INSUFFICIENT) {
                                return sql(() -> store.transitionPurchase(operationId, PurchasePhase.CHARGING, PurchasePhase.FAILED, "insufficient funds"))
                                        .thenCompose(ignored -> done(ShopStatus.INSUFFICIENT_FUNDS, operationId, tagId, offer, "insufficient funds"));
                            }
                            if (charge != ChargeResult.SUCCESS) {
                                return sql(() -> store.transitionPurchase(operationId, PurchasePhase.CHARGING, PurchasePhase.FAILED, "payment failed"))
                                        .thenCompose(ignored -> done(ShopStatus.PAYMENT_FAILED, operationId, tagId, offer, "withdraw failed"));
                            }
                            return sql(() -> store.transitionPurchase(operationId, PurchasePhase.CHARGING, PurchasePhase.CHARGED, null))
                                    .handle((marked, error) -> error == null && marked)
                                    .thenCompose(marked -> marked
                                            ? sql(() -> store.completePurchase(playerId, tagId, operationId))
                                            .handle((completed, error) -> error == null && completed)
                                            .thenCompose(completed -> completed
                                                    ? complete(playerId, tag, offer, operationId)
                                                    : refund(currency, playerId, tagId, offer, operationId, PurchasePhase.CHARGED))
                                            : refund(currency, playerId, tagId, offer, operationId, PurchasePhase.CHARGING));
                        }));
    }

    public BigDecimal balance(UUID playerId, DCTagOffer offer) {
        EcoProvider provider = providerFor(offer);
        return provider != null && provider.available() ? provider.balance(playerId) : null;
    }

    public boolean currencyAvailable(DCTagOffer offer) {
        EcoProvider provider = providerFor(offer);
        return provider != null && provider.available();
    }

    private EcoProvider providerFor(DCTagOffer offer) {
        if (offer.currency() == com.dracave.tags.handlers.EcoType.ITEM) {
            return new ItemEco(offer.itemMaterial());
        }
        return currencies.get(offer.currency());
    }

    private ChargeResult charge(EcoProvider provider, UUID playerId, BigDecimal amount) {
        Object lock = chargeLocks.computeIfAbsent(playerId, k -> new Object());
        try {
            synchronized (lock) {
                BigDecimal bal = provider.balance(playerId);
                if (bal == null) bal = BigDecimal.ZERO;
                if (bal.compareTo(amount) < 0) {
                    plugin.getLogger().warning("购买余额不足: player=" + playerId + " balance=" + bal.toPlainString() + " price=" + amount.toPlainString());
                    return ChargeResult.INSUFFICIENT;
                }
                boolean ok = provider.withdraw(playerId, amount);
                if (!ok) {
                    plugin.getLogger().warning("购买扣款失败: player=" + playerId + " price=" + amount.toPlainString() + " balance=" + bal.toPlainString() + " provider=" + provider.type().id());
                }
                return ok ? ChargeResult.SUCCESS : ChargeResult.FAILED;
            }
        } finally {
            chargeLocks.remove(playerId, lock);
        }
    }

    private CompletableFuture<ShopResult> complete(UUID playerId, DCTag tag, DCTagOffer offer, UUID operationId) {
        tagEngine.cacheUnlock(playerId, tag.id());
        CompletableFuture<ShopResult> future = main(() -> {
            Bukkit.getPluginManager().callEvent(new DCTagPurchasedEvent(
                    Bukkit.getPlayer(playerId), tag.id(), offer.currency(), offer.price(), operationId));
            return result(ShopStatus.SUCCESS, operationId, tag.id(), offer, "completed");
        });
        if (plugin.getConfig().getBoolean(Cfg.SHOP_AUTO_EQUIP, true)) {
            future = future.thenCompose(success -> tagEngine.equip(playerId, tag.id()).thenApply(equip -> success));
        }
        return future;
    }

    private CompletableFuture<ShopResult> refund(EcoProvider provider, UUID playerId, String tagId,
                                                 DCTagOffer offer, UUID operationId, PurchasePhase expectedState) {
        return main(() -> provider.refund(playerId, offer.price()))
                .thenCompose(refunded -> sql(() -> store.transitionPurchase(
                        operationId, expectedState, refunded ? PurchasePhase.REFUNDED : PurchasePhase.REFUND_PENDING, "unlock persistence failed"))
                        .exceptionally(error -> false)
                        .thenCompose(recorded -> {
                            if (refunded) {
                                sql(() -> store.markRefunded(operationId, true)).exceptionally(error -> false);
                            }
                            if (!recorded) {
                                plugin.getLogger().severe("购买退款状态未能落库，需人工核对: " + operationId);
                            }
                            ShopStatus status = refunded && recorded ? ShopStatus.REFUNDED : ShopStatus.REFUND_PENDING;
                            return done(status, operationId, tagId, offer, "unlock persistence failed");
                        }));
    }

    public void recoverInterruptedPurchases() {
        CompletableFuture.runAsync(() -> {
            try {
                for (PurchaseLog record : store.findStalePurchases(serviceStartedAt + 1L)) {
                    PurchasePhase phase;
                    try {
                        phase = PurchasePhase.valueOf(record.state());
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    switch (phase) {
                        case PENDING -> store.forcePurchaseState(record.operationId(), PurchasePhase.FAILED, "interrupted before charge");
                        case CHARGING -> {
                            warnRecovery(record, "扣款状态不明，已标为失败可重新购买");
                            store.forcePurchaseState(record.operationId(), PurchasePhase.FAILED, "charge interrupted during reload");
                        }
                        case CHARGED -> {
                            try {
                                store.completePurchase(record.playerId(), record.tagId(), record.operationId());
                                tagEngine.cacheUnlock(record.playerId(), record.tagId());
                                plugin.getLogger().info("热重载恢复购买: " + record.operationId() + " 已补发称号 " + record.tagId());
                            } catch (Exception ex) {
                                warnRecovery(record, "补发失败需人工处理: " + ex.getMessage());
                                store.forcePurchaseState(record.operationId(), PurchasePhase.REFUND_PENDING, "charged but completion failed during reload");
                            }
                        }
                        default -> {}
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().severe("扫描中断购买失败: " + ex.getMessage());
            }
        }, sqlExecutor);
    }

    private void warnRecovery(PurchaseLog record, String message) {
        plugin.getLogger().severe("购买恢复警告 operation=" + record.operationId()
                + " player=" + record.playerId() + " tag=" + record.tagId()
                + " currency=" + record.currency() + " amount=" + record.amount() + " - " + message);
    }

    public void close() {
        closed = true;
        sqlExecutor.shutdown();
        try {
            if (!sqlExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                sqlExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            sqlExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private enum ChargeResult {
        SUCCESS, INSUFFICIENT, FAILED
    }

    private CompletableFuture<Boolean> fire(Cancellable event) {
        return main(() -> {
            Bukkit.getPluginManager().callEvent((Event) event);
            return !event.isCancelled();
        });
    }

    private <T> CompletableFuture<T> main(Supplier<T> supplier) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (RuntimeException ex) {
                return CompletableFuture.failedFuture(ex);
            }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        SchedulerUtil.runTask(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (RuntimeException ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    private CompletableFuture<Boolean> sql(SqlBoolean operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.run();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }, sqlExecutor);
    }

    @FunctionalInterface
    private interface SqlBoolean {
        boolean run() throws SQLException;
    }

    private CompletableFuture<ShopResult> done(ShopStatus status, UUID operation, String tag,
                                                      DCTagOffer offer, String detail) {
        if (status != ShopStatus.SUCCESS && status != ShopStatus.ALREADY_UNLOCKED) {
            plugin.getLogger().warning("购买失败 [" + status + "] tag=" + tag + " reason=" + detail);
        }
        return CompletableFuture.completedFuture(result(status, operation, tag, offer, detail));
    }

    private static ShopResult result(ShopStatus status, UUID operation, String tag,
                                     DCTagOffer offer, String detail) {
        return new ShopResult(status, operation, tag,
                offer == null ? "" : offer.currency().id(),
                offer == null ? BigDecimal.ZERO : offer.price(),
                detail == null ? "" : detail);
    }
}
