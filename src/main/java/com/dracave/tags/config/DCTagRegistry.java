package com.dracave.tags.config;

import com.dracave.tags.handlers.DCTag;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class DCTagRegistry {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    private final JavaPlugin plugin;
    private volatile Map<String, DCTag> configured = Map.of();
    private final Map<String, DCTag> runtime = new ConcurrentHashMap<>();
    private final Map<String, DCTag> custom = new ConcurrentHashMap<>();
    private final Map<String, UUID> customOwners = new ConcurrentHashMap<>();

    public DCTagRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void replaceConfigured(Collection<DCTag> definitions) {
        Map<String, DCTag> loaded = new HashMap<>();
        for (DCTag definition : definitions) {
            String id = normalizeId(definition.id());
            if (!id.equals(definition.id()) || !VALID_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("invalid tag id from storage: " + definition.id());
            }
            if (loaded.put(id, definition) != null) {
                throw new IllegalArgumentException("duplicate tag id: " + id);
            }
        }
        this.configured = Map.copyOf(loaded);
        plugin.getLogger().info("已从数据库加载 " + this.configured.size() + " 个全局称号及 "
                + this.runtime.size() + " 个运行时称号");
        plugin.getLogger().warning("热重载可能导致经济插件连接异常，若购买扣款失败请重启服务器");
    }

    public DCTag get(String id) {
        if (id == null) {
            return null;
        }
        String key = normalizeId(id);
        DCTag dynamic = runtime.get(key);
        if (dynamic != null) {
            return dynamic;
        }
        DCTag customTag = custom.get(key);
        return customTag != null ? customTag : configured.get(key);
    }

    public List<DCTag> all() {
        Map<String, DCTag> merged = new LinkedHashMap<>();
        for (DCTag tag : configured.values()) {
            merged.put(tag.id(), tag);
        }
        runtime.forEach(merged::put);
        return merged.values().stream()
                .sorted(Comparator.comparingInt(DCTag::order).thenComparing(DCTag::id))
                .toList();
    }

    public List<DCTag> configured() {
        return configured.values().stream()
                .sorted(Comparator.comparingInt(DCTag::order).thenComparing(DCTag::id))
                .toList();
    }

    public Collection<String> defaultIds() {
        return all().stream().filter(DCTag::defaultUnlocked).map(DCTag::id).toList();
    }

    public boolean register(DCTag tag) {
        String id = normalizeId(tag.id());
        if (tag.id().equals(id) && VALID_ID.matcher(id).matches()
                && !configured.containsKey(id) && !runtime.containsKey(id) && !custom.containsKey(id)) {
            runtime.put(id, tag);
            return true;
        }
        return false;
    }

    public boolean unregister(String id) {
        return runtime.remove(normalizeId(id)) != null;
    }

    public void registerCustom(DCTag tag, UUID ownerId) {
        custom.put(tag.id(), tag);
        customOwners.put(tag.id(), ownerId);
    }

    public void unregisterCustom(String id) {
        String normalized = normalizeId(id);
        custom.remove(normalized);
        customOwners.remove(normalized);
    }

    public boolean availableTo(String id, UUID playerId) {
        UUID owner = customOwners.get(normalizeId(id));
        return owner == null || owner.equals(playerId);
    }

    public static String normalizeId(String id) {
        return id.strip().toLowerCase(Locale.ENGLISH);
    }
}
