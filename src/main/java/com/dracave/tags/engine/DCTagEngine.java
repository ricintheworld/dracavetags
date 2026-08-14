package com.dracave.tags.engine;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.api.DCTagResult;
import com.dracave.tags.api.event.DCTagEquipEvent;
import com.dracave.tags.api.event.DCTagRevokeEvent;
import com.dracave.tags.api.event.DCTagUnequipEvent;
import com.dracave.tags.api.event.DCTagUnlockEvent;
import com.dracave.tags.config.DCTagRegistry;
import com.dracave.tags.config.Cfg;
import com.dracave.tags.config.Locale;
import com.dracave.tags.config.Cfg;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.PlayerData;
import com.dracave.tags.render.DCTagRenderer;
import com.dracave.tags.storage.PlayerStore;
import com.dracave.tags.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class DCTagEngine {
    private final DraCaveTags plugin;
    private final DCTagRegistry registry;
    private final PlayerStore store;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CompletableFuture<PlayerData>> loadingFutures = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSyncedUpdatedAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLocalWriteAt = new ConcurrentHashMap<>();
    private volatile Consumer<UUID> syncPublisher = ignored -> {};
    private volatile Consumer<UUID> effectReconciler = ignored -> {};
    private volatile Consumer<UUID> rewardChecker = ignored -> {};
    private final Map<UUID, Long> lastEquipAt = new ConcurrentHashMap<>();
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "DraCaveTags-Database");
        thread.setDaemon(true);
        return thread;
    });

    public DCTagEngine(DraCaveTags plugin, DCTagRegistry registry, PlayerStore store) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
    }

    public CompletableFuture<PlayerData> load(UUID playerId) {
        PlayerData current = cache.get(playerId);
        if (current != null) {
            return CompletableFuture.completedFuture(current);
        }
        CompletableFuture<PlayerData> inFlight = loadingFutures.get(playerId);
        if (inFlight != null) {
            return inFlight;
        }
        CompletableFuture<PlayerData> future = new CompletableFuture<>();
        CompletableFuture<PlayerData> existing = loadingFutures.putIfAbsent(playerId, future);
        if (existing != null) {
            return existing;
        }
        loading.add(playerId);
        try {
            CompletableFuture.supplyAsync(() -> {
                try {
                    PlayerData ready = loadReady(playerId);
                    cache.put(playerId, ready);
                    effectReconciler.accept(playerId);
                    return ready;
                } catch (SQLException ex) {
                    plugin.getLogger().severe("加载玩家称号数据失败 " + playerId + ": " + ex.getMessage());
                    return null;
                } finally {
                    loading.remove(playerId);
                }
            }, databaseExecutor).whenComplete((ready, error) -> {
                loadingFutures.remove(playerId);
                future.complete(ready);
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            loadingFutures.remove(playerId);
            loading.remove(playerId);
            future.complete(null);
        }
        return future;
    }

    public PlayerData getCached(UUID playerId) {
        return cache.get(playerId);
    }

    public void reconcileEffects(UUID playerId) {
        effectReconciler.accept(playerId);
    }

    public boolean isLoading(UUID playerId) {
        return loading.contains(playerId);
    }

    public void unload(UUID playerId) {
        cache.remove(playerId);
        lastSyncedUpdatedAt.remove(playerId);
        lastEquipAt.remove(playerId);
        lastLocalWriteAt.remove(playerId);
    }

    public void setSyncPublisher(Consumer<UUID> syncPublisher) {
        this.syncPublisher = syncPublisher == null ? ignored -> {} : syncPublisher;
    }

    public void setEffectReconciler(Consumer<UUID> effectReconciler) {
        this.effectReconciler = effectReconciler == null ? ignored -> {} : effectReconciler;
    }

    public void setRewardChecker(Consumer<UUID> rewardChecker) {
        this.rewardChecker = rewardChecker == null ? ignored -> {} : rewardChecker;
    }

    public void synchronizeEquipped(Collection<UUID> playerIds) {
        if (playerIds.isEmpty()) {
            return;
        }
        try {
            Map<UUID, PlayerStore.EquippedSnap> snapshots = store.batchLoadEquipped(playerIds);
            for (UUID playerId : playerIds) {
                PlayerStore.EquippedSnap snapshot = snapshots.get(playerId);
                if (snapshot == null) {
                    continue;
                }
                PlayerData cached = cache.get(playerId);
                if (cached == null) {
                    continue;
                }
                boolean equippedChanged = !Objects.equals(cached.equippedId(), snapshot.equippedId());
                Long lastSynced = lastSyncedUpdatedAt.get(playerId);
                boolean timestampChanged = lastSynced == null || snapshot.updatedAt() != lastSynced;
                if (!equippedChanged && !timestampChanged) {
                    continue;
                }
                Long localWrite = lastLocalWriteAt.get(playerId);
                if (localWrite != null && System.currentTimeMillis() - localWrite < 2000L) {
                    continue;
                }
                PlayerData refreshed = loadReady(playerId);
                cache.put(playerId, refreshed);
                lastSyncedUpdatedAt.put(playerId, snapshot.updatedAt());
                if (equippedChanged) {
                    effectReconciler.accept(playerId);
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("同步跨服称号穿戴状态失败: " + ex.getMessage());
        }
    }

    public CompletableFuture<DCTagResult> unlock(UUID playerId, String rawTagId, int days) {
        String tagId = DCTagRegistry.normalizeId(rawTagId);
        if (registry.get(tagId) == null || !registry.availableTo(tagId, playerId)) {
            return completed(DCTagResult.TITLE_NOT_FOUND);
        }
        return withData(playerId).thenCompose(data -> {
            if (data == null) {
                return completed(DCTagResult.DATABASE_ERROR);
            }
            if (data.unlocked().contains(tagId)) {
                return completed(DCTagResult.ALREADY_UNLOCKED);
            }
            Player player = Bukkit.getPlayer(playerId);
            return fire(new DCTagUnlockEvent(player, tagId)).thenCompose(allowed ->
                    !allowed ? completed(DCTagResult.CANCELLED) : database(() -> {
                        if (!store.unlock(playerId, tagId, days)) {
                            return DCTagResult.ALREADY_UNLOCKED;
                        }
                        cache.computeIfPresent(playerId, (id, old) -> {
                            HashSet<String> ids = new HashSet<>(old.unlocked());
                            ids.add(tagId);
                            return old.withUnlocked(ids);
                        });
                        syncPublisher.accept(playerId);
                        rewardChecker.accept(playerId);
                        return DCTagResult.SUCCESS;
                    }));
        });
    }

    public CompletableFuture<DCTagResult> grant(UUID playerId, String rawTagId, int days) {
        return grant(playerId, rawTagId, days, false);
    }

    public CompletableFuture<DCTagResult> grant(UUID playerId, String rawTagId, int days, boolean force) {
        String tagId = DCTagRegistry.normalizeId(rawTagId);
        if (registry.get(tagId) == null || !registry.availableTo(tagId, playerId)) {
            return completed(DCTagResult.TITLE_NOT_FOUND);
        }
        return withData(playerId).thenCompose(data -> {
            if (data == null) {
                return completed(DCTagResult.DATABASE_ERROR);
            }
            return database(() -> {
                boolean extended = data.unlocked().contains(tagId)
                        ? (force ? store.setExpiry(playerId, tagId, days)
                                 : store.extend(playerId, tagId, days))
                        : store.unlock(playerId, tagId, days);
                if (extended) {
                    cache.computeIfPresent(playerId, (id, old) -> {
                        HashSet<String> ids = new HashSet<>(old.unlocked());
                        ids.add(tagId);
                        return old.withUnlocked(ids);
                    });
                    syncPublisher.accept(playerId);
                    rewardChecker.accept(playerId);
                }
                return extended ? DCTagResult.SUCCESS : DCTagResult.DATABASE_ERROR;
            });
        });
    }

    public CompletableFuture<DCTagResult> revoke(UUID playerId, String rawTagId) {
        String tagId = DCTagRegistry.normalizeId(rawTagId);
        if (registry.get(tagId) == null || !registry.availableTo(tagId, playerId)) {
            return completed(DCTagResult.TITLE_NOT_FOUND);
        }
        return withData(playerId).thenCompose(data -> {
            if (data == null) {
                return completed(DCTagResult.DATABASE_ERROR);
            }
            if (!data.unlocked().contains(tagId)) {
                return completed(DCTagResult.NOT_UNLOCKED);
            }
            return fire(new DCTagRevokeEvent(playerId, tagId)).thenCompose(allowed ->
                    !allowed ? completed(DCTagResult.CANCELLED) : database(() -> {
                        if (!store.revoke(playerId, tagId)) {
                            return DCTagResult.NOT_UNLOCKED;
                        }
                        cache.computeIfPresent(playerId, (id, old) -> {
                            HashSet<String> ids = new HashSet<>(old.unlocked());
                            ids.remove(tagId);
                            return new PlayerData(id, ids, tagId.equals(old.equippedId()) ? null : old.equippedId());
                        });
                        syncPublisher.accept(playerId);
                        effectReconciler.accept(playerId);
                        return DCTagResult.SUCCESS;
                    }));
        });
    }

    public CompletableFuture<DCTagResult> equip(UUID playerId, String rawTagId) {
        return equip(playerId, rawTagId, false);
    }

    public CompletableFuture<DCTagResult> equip(UUID playerId, String rawTagId, boolean bypassCooldown) {
        String tagId = DCTagRegistry.normalizeId(rawTagId);
        if (registry.get(tagId) == null || !registry.availableTo(tagId, playerId)) {
            return completed(DCTagResult.TITLE_NOT_FOUND);
        }
        int cooldownSeconds = plugin.getConfig().getInt(Cfg.DISPLAY_TOGGLES_COOLDOWN, 0);
        if (!bypassCooldown && cooldownSeconds > 0) {
            long now = System.currentTimeMillis();
            Long last = lastEquipAt.get(playerId);
            if (last != null && now - last < cooldownSeconds * 1000L) {
                long remaining = (cooldownSeconds * 1000L - (now - last) + 999L) / 1000L;
                Player cooldownPlayer = Bukkit.getPlayer(playerId);
                if (cooldownPlayer != null && cooldownPlayer.isOnline()) {
                    plugin.messages().send(cooldownPlayer, "cooldown",
                            Locale.text("seconds", Long.toString(remaining)));
                }
                return completed(DCTagResult.COOLDOWN);
            }
        }
        return withData(playerId).thenCompose(data -> {
            if (data == null) {
                return completed(DCTagResult.DATABASE_ERROR);
            }
            if (!data.unlocked().contains(tagId)) {
                return completed(DCTagResult.NOT_UNLOCKED);
            }
            if (tagId.equals(data.equippedId())) {
                return completed(DCTagResult.ALREADY_EQUIPPED);
            }
            Player player = Bukkit.getPlayer(playerId);
            return fire(new DCTagEquipEvent(player, data.equippedId(), tagId)).thenCompose(allowed ->
                    !allowed ? completed(DCTagResult.CANCELLED) : database(() -> {
                        if (!store.equip(playerId, tagId)) {
                            return DCTagResult.NOT_UNLOCKED;
                        }
                        cache.computeIfPresent(playerId, (id, old) -> old.withEquipped(tagId));
                        lastEquipAt.put(playerId, System.currentTimeMillis());
                        lastLocalWriteAt.put(playerId, System.currentTimeMillis());
                        syncPublisher.accept(playerId);
                        effectReconciler.accept(playerId);
                        return DCTagResult.SUCCESS;
                    }));
        });
    }

    public CompletableFuture<DCTagResult> clear(UUID playerId) {
        return withData(playerId).thenCompose(data -> {
            if (data == null) {
                return completed(DCTagResult.DATABASE_ERROR);
            }
            if (data.equippedId() == null) {
                return completed(DCTagResult.SUCCESS);
            }
            Player player = Bukkit.getPlayer(playerId);
            return fire(new DCTagUnequipEvent(player, data.equippedId())).thenCompose(allowed ->
                    !allowed ? completed(DCTagResult.CANCELLED) : database(() -> {
                        store.equip(playerId, null);
                        cache.computeIfPresent(playerId, (id, old) -> old.withEquipped(null));
                        lastLocalWriteAt.put(playerId, System.currentTimeMillis());
                        syncPublisher.accept(playerId);
                        effectReconciler.accept(playerId);
                        return DCTagResult.SUCCESS;
                    }));
        });
    }

    public DCTag equipped(UUID playerId) {
        PlayerData data = cache.get(playerId);
        return data == null ? null : registry.get(data.equippedId());
    }

    public void cacheUnlock(UUID playerId, String tagId) {
        cache.computeIfPresent(playerId, (id, old) -> {
            HashSet<String> ids = new HashSet<>(old.unlocked());
            ids.add(tagId);
            return old.withUnlocked(ids);
        });
        syncPublisher.accept(playerId);
        rewardChecker.accept(playerId);
    }

    public void removeCachedTag(UUID playerId, String tagId) {
        cache.computeIfPresent(playerId, (id, old) -> {
            HashSet<String> ids = new HashSet<>(old.unlocked());
            ids.remove(tagId);
            return new PlayerData(id, ids, tagId.equals(old.equippedId()) ? null : old.equippedId());
        });
        syncPublisher.accept(playerId);
        effectReconciler.accept(playerId);
    }

    public void removeCachedTagFromAll(String tagId) {
        cache.keySet().forEach(playerId -> removeCachedTag(playerId, tagId));
    }

    public long lastLocalWriteAt(UUID playerId) {
        Long value = lastLocalWriteAt.get(playerId);
        return value == null ? 0L : value;
    }

    public void applyRemoteEquip(UUID playerId, String tagId) {
        if (tagId != null) {
            DCTag candidate = registry.get(tagId);
            if (candidate == null) {
                return;
            }
        }
        PlayerData current = cache.get(playerId);
        if (current == null) {
            return;
        }
        if (Objects.equals(current.equippedId(), tagId)) {
            return;
        }
        cache.put(playerId, current.withEquipped(tagId));
        effectReconciler.accept(playerId);
    }

    public void purgeExpired(UUID playerId) {
        PlayerData data = cache.get(playerId);
        if (data == null || data.unlocked().isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Long> removed = store.purgeExpired(playerId);
                if (removed.isEmpty()) {
                    return;
                }
                cache.computeIfPresent(playerId, (id, old) -> {
                    HashSet<String> ids = new HashSet<>(old.unlocked());
                    ids.removeAll(removed.keySet());
                    String equipped = old.equippedId();
                    if (equipped != null && removed.containsKey(equipped)) {
                        equipped = null;
                    }
                    return new PlayerData(id, ids, equipped);
                });
                syncPublisher.accept(playerId);
                effectReconciler.accept(playerId);
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    for (String tagId : removed.keySet()) {
                        DCTag tag = registry.get(tagId);
                        if (tag != null) {
                            plugin.messages().send(player, "overdue",
                                    Locale.parsed("tag", DCTagRenderer.miniMessage(tag, System.currentTimeMillis())));
                        }
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("清理过期称号失败 " + playerId + ": " + ex.getMessage());
            }
        }, databaseExecutor);
    }

    public void close() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("数据库任务未能在 10 秒内排空");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private CompletableFuture<PlayerData> withData(UUID playerId) {
        PlayerData cached = cache.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return load(playerId);
    }

    private CompletableFuture<DCTagResult> database(SqlOperation operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.run();
            } catch (SQLException ex) {
                plugin.getLogger().severe("称号数据库操作失败: " + ex.getMessage());
                return DCTagResult.DATABASE_ERROR;
            }
        }, databaseExecutor);
    }

    private PlayerData loadReady(UUID playerId) throws SQLException {
        PlayerData loaded = store.load(playerId);
        HashSet<String> defaults = new HashSet<>(registry.defaultIds());
        defaults.removeAll(loaded.unlocked());
        for (String tagId : defaults) {
            store.unlock(playerId, tagId, 0);
        }
        HashSet<String> all = new HashSet<>(loaded.unlocked());
        all.addAll(defaults);
        return new PlayerData(playerId, all, loaded.equippedId(), loaded.expirations());
    }

    private CompletableFuture<Boolean> fire(Cancellable event) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent((Event) event);
            return CompletableFuture.completedFuture(!event.isCancelled());
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        SchedulerUtil.runTask(plugin, () -> {
            Bukkit.getPluginManager().callEvent((Event) event);
            future.complete(!event.isCancelled());
        });
        return future;
    }

    private static <T> CompletableFuture<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    @FunctionalInterface
    private interface SqlOperation {
        DCTagResult run() throws SQLException;
    }
}
