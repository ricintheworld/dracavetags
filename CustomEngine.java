package com.dracave.tags.engine;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.api.event.CustomCreateEvent;
import com.dracave.tags.api.event.CustomCreatedEvent;
import com.dracave.tags.api.event.CustomDeletedEvent;
import com.dracave.tags.config.DCTagRegistry;
import com.dracave.tags.config.Cfg;
import com.dracave.tags.handlers.CustomDCTag;
import com.dracave.tags.handlers.CustomDraft;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.DCTagAnim;
import com.dracave.tags.handlers.DCTagType;
import com.dracave.tags.storage.CustomDCTagStore;
import com.dracave.tags.storage.QuotaStore;
import com.dracave.tags.util.ItemResolver;
import com.dracave.tags.util.SchedulerUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class CustomEngine {
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");

    private final DraCaveTags plugin;
    private final CustomDCTagStore store;
    private final QuotaStore quotaStore;
    private final DCTagRegistry registry;
    private final DCTagEngine tagEngine;
    private final Map<String, CustomDCTag> definitions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Object> createLocks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DraCaveTags-Custom");
        t.setDaemon(true);
        return t;
    });

    public CustomEngine(DraCaveTags plugin, CustomDCTagStore store, QuotaStore quotaStore,
                        DCTagRegistry registry, DCTagEngine tagEngine) {
        this.plugin = plugin;
        this.store = store;
        this.quotaStore = quotaStore;
        this.registry = registry;
        this.tagEngine = tagEngine;
    }

    public void loadAll() throws Exception {
        for (CustomDCTag tag : store.loadActive()) {
            publish(tag);
        }
    }

    public List<CustomDCTag> ownedBy(UUID owner) {
        return definitions.values().stream()
                .filter(t -> t.ownerId().equals(owner))
                .sorted(Comparator.comparingLong(CustomDCTag::createdAt))
                .toList();
    }

    public DCTag rendered(String id) {
        return registry.get(id);
    }

    public int limit(Player player) {
        if (player.hasPermission("dracave.tags.custom.limit.unlimited")) {
            return Integer.MAX_VALUE;
        }
        int max = 0;
        for (PermissionAttachmentInfo permission : player.getEffectivePermissions()) {
            String node = permission.getPermission();
            if (permission.getValue() && node.startsWith("dracave.tags.custom.limit.")) {
                try {
                    max = Math.max(max, Integer.parseInt(node.substring("dracave.tags.custom.limit.".length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        int adminQuota = 0;
        try {
            adminQuota = quotaStore.quota(player.getUniqueId());
        } catch (Exception ignored) {
        }
        return Math.max(max, adminQuota);
    }

    public CompletableFuture<Result> create(Player player, CustomDraft raw) {
        if (!Bukkit.isPrimaryThread()) {
            return onMainThread(() -> create(player, raw));
        }
        if (raw == null || raw.type() == null) {
            return done(Result.INVALID);
        }
        if (!plugin.getConfig().getBoolean(Cfg.CUSTOM_ENABLED, true)) {
            return done(Result.DISABLED);
        }
        boolean dynamic = raw.type().dynamic();
        if (!player.hasPermission(dynamic ? "dracave.tags.custom.dynamic" : "dracave.tags.custom.static")) {
            return done(Result.NO_PERMISSION);
        }
        UUID playerId = player.getUniqueId();
        int playerLimit = limit(player);
        if (ownedBy(playerId).size() >= playerLimit) {
            return done(Result.LIMIT_REACHED);
        }
        CustomDraft draft;
        try {
            draft = validate(raw);
        } catch (IllegalArgumentException ex) {
            return done(Result.INVALID);
        }
        CustomCreateEvent event = new CustomCreateEvent(player, draft);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return done(Result.INVALID);
        }
        long now = System.currentTimeMillis();
        CustomDCTag tag = new CustomDCTag(
                "custom_" + UUID.randomUUID().toString().replace("-", ""),
                playerId, draft.text(), draft.type(), draft.colors(), draft.frames(),
                draft.periodTicks(), draft.icon(), 1, now, now);
        return CompletableFuture.supplyAsync(() -> {
            Object lock = createLocks.computeIfAbsent(playerId, k -> new Object());
            try {
                synchronized (lock) {
                    if (ownedBy(playerId).size() >= playerLimit) {
                        return Result.LIMIT_REACHED;
                    }
                    try {
                        store.create(tag);
                    } catch (Exception ex) {
                        plugin.getLogger().severe("创建自定义称号失败: " + ex.getMessage());
                        return Result.DATABASE_ERROR;
                    }
                    publish(tag);
                    tagEngine.cacheUnlock(playerId, tag.id());
                    scheduleEvent(() -> new CustomCreatedEvent(tag));
                    return Result.SUCCESS;
                }
            } finally {
                createLocks.remove(playerId, lock);
            }
        }, executor);
    }

    public CompletableFuture<Result> update(Player player, String id, CustomDraft raw) {
        if (!Bukkit.isPrimaryThread()) {
            return onMainThread(() -> update(player, id, raw));
        }
        if (raw == null || raw.type() == null) {
            return done(Result.INVALID);
        }
        CustomDCTag old = definitions.get(id);
        if (old == null || !old.ownerId().equals(player.getUniqueId())) {
            return done(Result.NOT_FOUND);
        }
        if (!player.hasPermission(raw.type().dynamic() ? "dracave.tags.custom.dynamic" : "dracave.tags.custom.static")) {
            return done(Result.NO_PERMISSION);
        }
        CustomDraft draft;
        try {
            draft = validate(raw);
        } catch (IllegalArgumentException ex) {
            return done(Result.INVALID);
        }
        CustomDCTag changed = new CustomDCTag(old.id(), old.ownerId(), draft.text(), draft.type(), draft.colors(),
                draft.frames(), draft.periodTicks(), draft.icon(), old.revision() + 1, old.createdAt(), System.currentTimeMillis());
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!store.update(changed, old.revision())) {
                    return Result.CONFLICT;
                }
                publish(changed);
                return Result.SUCCESS;
            } catch (Exception ex) {
                return Result.DATABASE_ERROR;
            }
        }, executor);
    }

    public CompletableFuture<Result> delete(Player player, String id) {
        if (!Bukkit.isPrimaryThread()) {
            return onMainThread(() -> delete(player, id));
        }
        UUID playerId = player.getUniqueId();
        CustomDCTag tag = definitions.get(id);
        if (tag == null || !tag.ownerId().equals(playerId)) {
            return done(Result.NOT_FOUND);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!store.delete(playerId, id)) {
                    return Result.NOT_FOUND;
                }
                definitions.remove(id);
                registry.unregisterCustom(id);
                tagEngine.removeCachedTagFromAll(id);
                scheduleEvent(() -> new CustomDeletedEvent(playerId, id));
                return Result.SUCCESS;
            } catch (Exception ex) {
                return Result.DATABASE_ERROR;
            }
        }, executor);
    }

    private CustomDraft validate(CustomDraft input) {
        String text = clean(input.text());
        List<String> frames = input.frames().stream().map(this::clean).toList();
        List<String> colors = input.colors().stream().map(c -> {
            if (!COLOR.matcher(c).matches()) {
                throw new IllegalArgumentException("invalid color");
            }
            return c.toUpperCase(Locale.ROOT);
        }).toList();
        int maxColors = plugin.getConfig().getInt(Cfg.CUSTOM_DYNAMIC_MAX_COLORS, 5);
        if (colors.size() > maxColors) {
            throw new IllegalArgumentException("too many colors");
        }
        if ((input.type() == DCTagType.FLOWING_GRADIENT || input.type() == DCTagType.FLASHING_COLORS) && colors.size() < 2) {
            throw new IllegalArgumentException("too few colors");
        }
        if (input.type() == DCTagType.TEXT_FRAMES) {
            int maxFrames = plugin.getConfig().getInt(Cfg.CUSTOM_DYNAMIC_MAX_TEXT_FRAMES, 10);
            if (frames.size() < 2 || frames.size() > maxFrames) {
                throw new IllegalArgumentException("invalid frames");
            }
        }
        int period = input.periodTicks();
        if (input.type().dynamic()) {
            int min = plugin.getConfig().getInt(Cfg.CUSTOM_DYNAMIC_MIN_PERIOD, 5);
            int max = plugin.getConfig().getInt(Cfg.CUSTOM_DYNAMIC_MAX_PERIOD, 200);
            if (period < min || period > max) {
                throw new IllegalArgumentException("invalid period");
            }
        }
        String icon = ItemResolver.isValid(input.icon()) ? input.icon() : "NAME_TAG";
        return new CustomDraft(text, input.type(), colors, frames, period, icon);
    }

    private String clean(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("missing text");
        }
        String text = java.text.Normalizer.normalize(raw.trim(), java.text.Normalizer.Form.NFC);
        int max = plugin.getConfig().getInt(Cfg.CUSTOM_TEXT_MAX_LENGTH, 16);
        if (text.isEmpty() || text.codePointCount(0, text.length()) > max || text.matches(".*[<>§\\p{Cntrl}\\p{Cf}].*")) {
            throw new IllegalArgumentException("invalid text");
        }
        for (String word : plugin.getConfig().getStringList(Cfg.CUSTOM_FILTER_BLOCKED_WORDS)) {
            if (!word.isBlank() && text.toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("blocked text");
            }
        }
        return text;
    }

    private void publish(CustomDCTag custom) {
        definitions.put(custom.id(), custom);
        MiniMessage mini = MiniMessage.miniMessage();
        String escaped = mini.escapeTags(custom.text());
        List<String> escapedFrames = custom.frames().stream().map(mini::escapeTags).toList();
        DCTagAnim animation = switch (custom.type()) {
            case STATIC -> null;
            case FLOWING_GRADIENT -> new DCTagAnim(DCTagAnim.AnimType.FLOWING_GRADIENT, custom.colors(), List.of(), custom.periodTicks());
            case TEXT_FRAMES -> new DCTagAnim(DCTagAnim.AnimType.TEXT_FRAMES, List.of(), escapedFrames, custom.periodTicks());
            case RAINBOW -> DCTagAnim.rainbow(custom.periodTicks());
            case FLASHING_COLORS -> new DCTagAnim(DCTagAnim.AnimType.FLASHING_COLORS, custom.colors(), List.of(), custom.periodTicks());
        };
        String display = custom.type() == DCTagType.STATIC && !custom.colors().isEmpty()
                ? "<" + custom.colors().get(0) + ">" + escaped + "</" + custom.colors().get(0) + ">"
                : escaped;
        registry.registerCustom(new DCTag(custom.id(), display, List.of("<gray>玩家自定义称号"),
                custom.icon(), 0, false, "", animation, null, List.of(), false, List.of(), null, 0), custom.ownerId());
    }

    private <T> CompletableFuture<T> onMainThread(Supplier<CompletableFuture<T>> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        SchedulerUtil.runTask(plugin, () -> {
            try {
                action.get().whenComplete((value, error) -> {
                    if (error == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(error);
                    }
                });
            } catch (RuntimeException ex) {
                result.completeExceptionally(ex);
            }
        });
        return result;
    }

    private void scheduleEvent(Supplier<Event> eventSupplier) {
        try {
            SchedulerUtil.runTask(plugin, () -> {
                Event event = eventSupplier.get();
                Bukkit.getPluginManager().callEvent(event);
            });
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("自定义称号事件投递失败: " + ex.getMessage());
        }
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10L, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("自定义称号数据库任务未能在 10 秒内排空");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static <T> CompletableFuture<T> done(T result) {
        return CompletableFuture.completedFuture(result);
    }

    public enum Result {
        SUCCESS, DISABLED, NO_PERMISSION, LIMIT_REACHED, INVALID, NOT_FOUND, CONFLICT, DATABASE_ERROR
    }
}
