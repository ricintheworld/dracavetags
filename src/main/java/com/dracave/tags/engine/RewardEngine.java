package com.dracave.tags.engine;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.config.Locale;
import com.dracave.tags.config.Cfg;
import com.dracave.tags.economy.EcoProvider;
import com.dracave.tags.economy.EcoRegistry;
import com.dracave.tags.handlers.EcoType;
import com.dracave.tags.handlers.PlayerData;
import com.dracave.tags.handlers.RewardCfg;
import com.dracave.tags.handlers.RewardKind;
import com.dracave.tags.storage.RewardStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class RewardEngine implements AutoCloseable {
    private final DraCaveTags plugin;
    private final DCTagEngine tags;
    private final RewardStore store;
    private final EcoRegistry currencies;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DraCaveTags-Reward");
        t.setDaemon(true);
        return t;
    });

    public RewardEngine(DraCaveTags plugin, DCTagEngine tags, RewardStore store, EcoRegistry currencies) {
        this.plugin = plugin;
        this.tags = tags;
        this.store = store;
        this.currencies = currencies;
    }

    public void check(UUID playerId) {
        CompletableFuture.runAsync(() -> {
            try {
                PlayerData data = tags.getCached(playerId);
                if (data == null) {
                    return;
                }
                int count = data.unlocked().size();
                Player player = Bukkit.getPlayer(playerId);
                for (RewardCfg reward : store.findAll()) {
                    if (reward.number() <= count && !store.isClaimed(playerId, reward.id())
                            && player != null && player.isOnline()) {
                        plugin.messages().send(player, "gui-reward-count",
                                Locale.text("number", Integer.toString(reward.number())));
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("检查奖励达成失败: " + ex.getMessage());
            }
        }, executor);
    }

    public CompletableFuture<ClaimResult> claim(Player player, long rewardId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                RewardCfg reward = store.findById(rewardId);
                if (reward == null) {
                    return ClaimResult.NOT_FOUND;
                }
                PlayerData data = tags.getCached(player.getUniqueId());
                int count = data == null ? 0 : data.unlocked().size();
                if (reward.number() > count) {
                    return ClaimResult.NOT_MET;
                }
                if (store.isClaimed(player.getUniqueId(), rewardId)) {
                    return ClaimResult.ALREADY_CLAIMED;
                }
                if (!grant(player, reward)) {
                    return ClaimResult.UNAVAILABLE;
                }
                store.claim(player.getUniqueId(), rewardId);
                return ClaimResult.SUCCESS;
            } catch (Exception ex) {
                plugin.getLogger().warning("领取奖励失败: " + ex.getMessage());
                return ClaimResult.FAILED;
            }
        }, executor);
    }

    public List<RewardCfg> all() {
        try {
            return store.findAll();
        } catch (Exception ex) {
            return List.of();
        }
    }

    public boolean isClaimed(UUID playerId, long rewardId) {
        try {
            return store.isClaimed(playerId, rewardId);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean grant(Player player, RewardCfg reward) {
        EcoProvider provider = switch (reward.kind()) {
            case VAULT -> currencies.get(EcoType.VAULT);
            case PLAYER_POINTS -> currencies.get(EcoType.PLAYER_POINTS);
            case COIN -> currencies.get(EcoType.COIN);
        };
        if (provider == null || !provider.available()) {
            return false;
        }
        return provider.refund(player.getUniqueId(), BigDecimal.valueOf(reward.amount()));
    }

    public static String rewardTypeDisplay(RewardKind kind, DraCaveTags plugin) {
        return switch (kind) {
            case VAULT -> plugin.getConfig().getString("shop.currencies.vault.display", "金币");
            case PLAYER_POINTS -> plugin.getConfig().getString("shop.currencies.playerpoints.display", "点券");
            case COIN -> plugin.getConfig().getString("shop.currencies.coin.display", "称号币");
        };
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("奖励任务未能在 5 秒内排空");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public enum ClaimResult {
        SUCCESS, NOT_FOUND, NOT_MET, ALREADY_CLAIMED, UNAVAILABLE, FAILED
    }
}
