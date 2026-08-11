package com.dracave.tags.api;

import com.dracave.tags.config.DCTagRegistry;
import com.dracave.tags.engine.CustomEngine;
import com.dracave.tags.engine.DCTagEngine;
import com.dracave.tags.engine.ShopEngine;
import com.dracave.tags.handlers.CustomDCTag;
import com.dracave.tags.handlers.CustomDraft;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.PlayerData;
import com.dracave.tags.render.DCTagRenderer;
import com.dracave.tags.storage.CoinStore;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TagsAPI {
    private static volatile DCTagEngine tagEngine;
    private static volatile DCTagRegistry registry;
    private static volatile ShopEngine shop;
    private static volatile CustomEngine custom;
    private static volatile CoinStore coinStore;

    private TagsAPI() {
    }

    public static void bind(DCTagEngine engine, DCTagRegistry tagRegistry, ShopEngine purchaseEngine) {
        tagEngine = engine;
        registry = tagRegistry;
        shop = purchaseEngine;
    }

    public static void bindCoin(CoinStore store) {
        coinStore = store;
    }

    public static void bindCustomTitles(CustomEngine customEngine) {
        custom = customEngine;
    }

    public static void unbind() {
        tagEngine = null;
        registry = null;
        shop = null;
        custom = null;
        coinStore = null;
    }

    public static Optional<DCTag> getTag(String id) {
        DCTagRegistry current = registry;
        return Optional.ofNullable(current == null ? null : current.get(id));
    }

    public static List<DCTag> getTags() {
        DCTagRegistry current = registry;
        return current == null ? List.of() : current.all();
    }

    public static Set<String> getUnlockedTags(UUID playerId) {
        DCTagEngine current = tagEngine;
        PlayerData data = current == null ? null : current.getCached(playerId);
        return data == null ? Set.of() : data.unlocked();
    }

    public static boolean isUnlocked(UUID playerId, String tagId) {
        return getUnlockedTags(playerId).contains(DCTagRegistry.normalizeId(tagId));
    }

    public static Optional<DCTag> getEquippedTag(UUID playerId) {
        DCTagEngine current = tagEngine;
        return Optional.ofNullable(current == null ? null : current.equipped(playerId));
    }

    public static String getMiniMessage(UUID playerId) {
        return getEquippedTag(playerId).map(tag -> DCTagRenderer.miniMessage(tag, System.currentTimeMillis())).orElse("");
    }

    public static String getPlainText(UUID playerId) {
        return getEquippedTag(playerId).map(tag -> DCTagRenderer.plain(tag, System.currentTimeMillis())).orElse("");
    }

    public static String getLegacyAmpersand(UUID playerId) {
        return getEquippedTag(playerId).map(tag -> DCTagRenderer.legacyAmpersand(tag, System.currentTimeMillis())).orElse("");
    }

    public static String getLegacySection(UUID playerId) {
        return getEquippedTag(playerId).map(tag -> DCTagRenderer.legacySection(tag, System.currentTimeMillis())).orElse("");
    }

    public static Component getComponent(UUID playerId) {
        return getEquippedTag(playerId).map(tag -> DCTagRenderer.component(tag, System.currentTimeMillis())).orElse(Component.empty());
    }

    public static CompletableFuture<DCTagResult> unlock(UUID playerId, String tagId) {
        DCTagEngine current = tagEngine;
        return current == null ? unavailable() : current.unlock(playerId, tagId, 0);
    }

    public static CompletableFuture<DCTagResult> unlock(UUID playerId, String tagId, int days) {
        DCTagEngine current = tagEngine;
        return current == null ? unavailable() : current.unlock(playerId, tagId, days);
    }

    public static CompletableFuture<DCTagResult> revoke(UUID playerId, String tagId) {
        DCTagEngine current = tagEngine;
        return current == null ? unavailable() : current.revoke(playerId, tagId);
    }

    public static CompletableFuture<DCTagResult> equip(UUID playerId, String tagId) {
        DCTagEngine current = tagEngine;
        return current == null ? unavailable() : current.equip(playerId, tagId);
    }

    public static CompletableFuture<DCTagResult> unequip(UUID playerId) {
        DCTagEngine current = tagEngine;
        return current == null ? unavailable() : current.clear(playerId);
    }

    public static CompletableFuture<ShopResult> purchase(UUID playerId, String tagId) {
        ShopEngine current = shop;
        return current == null
                ? CompletableFuture.completedFuture(new ShopResult(ShopStatus.SERVICE_UNAVAILABLE, null, tagId,
                "", BigDecimal.ZERO, "unavailable"))
                : current.purchase(playerId, tagId);
    }

    public static boolean registerRuntimeTag(DCTag tag) {
        DCTagRegistry current = registry;
        return current != null && current.register(tag);
    }

    public static boolean unregisterRuntimeTag(String tagId) {
        DCTagRegistry current = registry;
        return current != null && current.unregister(tagId);
    }

    public static List<CustomDCTag> getCustomTags(UUID ownerId) {
        CustomEngine current = custom;
        return current == null ? List.of() : current.ownedBy(ownerId);
    }

    public static CompletableFuture<CustomEngine.Result> createCustomTag(Player player, CustomDraft draft) {
        CustomEngine current = custom;
        return current == null ? CompletableFuture.completedFuture(CustomEngine.Result.DISABLED) : current.create(player, draft);
    }

    public static CompletableFuture<CustomEngine.Result> updateCustomTag(Player player, String id, CustomDraft draft) {
        CustomEngine current = custom;
        return current == null ? CompletableFuture.completedFuture(CustomEngine.Result.DISABLED) : current.update(player, id, draft);
    }

    public static CompletableFuture<CustomEngine.Result> deleteCustomTag(Player player, String id) {
        CustomEngine current = custom;
        return current == null ? CompletableFuture.completedFuture(CustomEngine.Result.DISABLED) : current.delete(player, id);
    }

    public static long getCoinBalance(UUID playerId) {
        CoinStore store = coinStore;
        if (store == null) {
            return 0L;
        }
        try {
            return store.balance(playerId);
        } catch (SQLException ex) {
            return 0L;
        }
    }

    private static CompletableFuture<DCTagResult> unavailable() {
        return CompletableFuture.completedFuture(DCTagResult.SERVICE_UNAVAILABLE);
    }
}
