package com.dracave.tags.engine;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.handlers.ChatColorMode;
import com.dracave.tags.handlers.ChatColorPreference;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.render.DCTagRenderer;
import com.dracave.tags.storage.ChatColorStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class ChatColorEngine {
    private static final Pattern HEX = Pattern.compile("#[0-9A-F]{6}");
    private final DraCaveTags plugin;
    private final ChatColorStore store;
    private final DCTagEngine tagEngine;
    private final Map<UUID, ChatColorPreference> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<ChatColorPreference>> loading = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "DraCaveTags-ChatColor");
        thread.setDaemon(true);
        return thread;
    });

    public ChatColorEngine(DraCaveTags plugin, ChatColorStore store, DCTagEngine tagEngine) {
        this.plugin = plugin;
        this.store = store;
        this.tagEngine = tagEngine;
    }

    public CompletableFuture<ChatColorPreference> load(UUID playerId) {
        ChatColorPreference current = cache.get(playerId);
        if (current != null) {
            return CompletableFuture.completedFuture(current);
        }
        return loading.computeIfAbsent(playerId, id -> CompletableFuture.supplyAsync(() -> {
            try {
                ChatColorPreference loaded = store.load(id);
                if (loaded == null) {
                    loaded = defaultPreference();
                }
                cache.put(id, loaded);
                return loaded;
            } catch (SQLException ex) {
                plugin.getLogger().warning("加载玩家聊天颜色失败 " + id + ": " + ex.getMessage());
                return defaultPreference();
            }
        }, executor).whenComplete((result, error) -> loading.remove(id)));
    }

    public ChatColorPreference preference(UUID playerId) {
        return cache.getOrDefault(playerId, defaultPreference());
    }

    public CompletableFuture<Boolean> setMode(UUID playerId, ChatColorMode mode) {
        return save(playerId, new ChatColorPreference(mode, null));
    }

    public CompletableFuture<Boolean> setCustom(UUID playerId, String rawColor) {
        String color = normalize(rawColor);
        if (color == null) {
            return CompletableFuture.completedFuture(false);
        }
        return save(playerId, new ChatColorPreference(ChatColorMode.CUSTOM, color));
    }

    private CompletableFuture<Boolean> save(UUID playerId, ChatColorPreference preference) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                store.save(playerId, preference);
                cache.put(playerId, preference);
                return true;
            } catch (SQLException ex) {
                plugin.getLogger().warning("保存玩家聊天颜色失败 " + playerId + ": " + ex.getMessage());
                return false;
            }
        }, executor);
    }

    public String colorHex(UUID playerId) {
        ChatColorPreference preference = preference(playerId);
        return switch (preference.mode()) {
            case DEFAULT -> "";
            case CUSTOM -> preference.customColor() == null ? "" : preference.customColor();
            case TITLE -> titleColor(playerId);
        };
    }

    public String miniMessage(UUID playerId) {
        String hex = colorHex(playerId);
        return hex.isEmpty() ? "" : "<" + hex + ">";
    }

    public String legacyAmpersand(UUID playerId) {
        String hex = colorHex(playerId);
        return hex.isEmpty() ? "" : "&" + hex;
    }

    public String legacySection(UUID playerId) {
        String hex = colorHex(playerId);
        if (hex.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder("§x");
        for (int i = 1; i < hex.length(); i++) {
            result.append('§').append(hex.charAt(i));
        }
        return result.toString();
    }

    private String titleColor(UUID playerId) {
        DCTag tag = tagEngine.equipped(playerId);
        if (tag == null) {
            return "";
        }
        List<TextColor> colors = new ArrayList<>();
        collectColors(DCTagRenderer.component(tag, System.currentTimeMillis()), colors);
        if (colors.isEmpty()) {
            return "";
        }
        String source = plugin.getConfig().getString("chat-color.title-color-source", "first")
                .toLowerCase(Locale.ROOT);
        TextColor selected = switch (source) {
            case "last" -> colors.get(colors.size() - 1);
            case "average" -> average(colors);
            default -> colors.get(0);
        };
        return selected.asHexString().toUpperCase(Locale.ROOT);
    }

    private ChatColorPreference defaultPreference() {
        ChatColorMode mode = ChatColorMode.parse(
                plugin.getConfig().getString("chat-color.default-mode", ChatColorMode.TITLE.name()));
        return mode == ChatColorMode.CUSTOM ? ChatColorPreference.FOLLOW_TITLE
                : new ChatColorPreference(mode, null);
    }

    private static void collectColors(Component component, List<TextColor> colors) {
        TextColor color = component.style().color();
        if (color != null) {
            colors.add(color);
        }
        for (Component child : component.children()) {
            collectColors(child, colors);
        }
    }

    private static TextColor average(List<TextColor> colors) {
        long red = 0;
        long green = 0;
        long blue = 0;
        for (TextColor color : colors) {
            red += color.red();
            green += color.green();
            blue += color.blue();
        }
        int size = colors.size();
        return TextColor.color((int) (red / size), (int) (green / size), (int) (blue / size));
    }

    public static String normalize(String rawColor) {
        if (rawColor == null) {
            return null;
        }
        String color = rawColor.trim().toUpperCase(Locale.ROOT);
        if (!color.startsWith("#")) {
            color = "#" + color;
        }
        return HEX.matcher(color).matches() ? color : null;
    }

    public void unload(UUID playerId) {
        cache.remove(playerId);
        loading.remove(playerId);
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}