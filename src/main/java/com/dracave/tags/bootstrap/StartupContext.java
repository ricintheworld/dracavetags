package com.dracave.tags.bootstrap;

import com.dracave.tags.DraCaveTags;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 启动上下文。模块间的依赖容器，模块通过 bind 注册服务，
 * 后续模块通过 get/getIfPresent 取用，避免装配顺序硬编码。
 * <p>
 * 学习自 2.0.1 的 StartupContext，应用到 2.0.0 的服务装配。
 */
public final class StartupContext {

    private final DraCaveTags plugin;
    private final ConcurrentMap<Class<?>, Object> services = new ConcurrentHashMap<>();

    public StartupContext(@NotNull DraCaveTags plugin) {
        this.plugin = plugin;
    }

    public @NotNull DraCaveTags plugin() {
        return plugin;
    }

    public <T> void bind(@NotNull Class<T> type, @NotNull T instance) {
        services.put(type, instance);
    }

    @SuppressWarnings("unchecked")
    public <T> @NotNull Optional<T> getIfPresent(@NotNull Class<T> type) {
        return Optional.ofNullable((T) services.get(type));
    }

    public <T> @NotNull T get(@NotNull Class<T> type) {
        T value = getIfPresent(type).orElse(null);
        if (value == null) {
            throw new IllegalStateException("服务未就绪: " + type.getName());
        }
        return value;
    }

    public <T> T getOrNull(@NotNull Class<T> type) {
        return getIfPresent(type).orElse(null);
    }
}
