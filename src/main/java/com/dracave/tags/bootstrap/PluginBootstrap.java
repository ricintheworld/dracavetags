package com.dracave.tags.bootstrap;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.module.CommandModule;
import com.dracave.tags.module.ConfigModule;
import com.dracave.tags.module.DatabaseModule;
import com.dracave.tags.module.ListenerModule;
import com.dracave.tags.module.ModuleManager;
import com.dracave.tags.module.PlaceholderModule;
import org.jetbrains.annotations.NotNull;

/**
 * 插件启动与关停编排。装配启动上下文与模块管理器，按依赖顺序启用模块。
 * <p>
 * 模块启用顺序：Config → Database → Command → Listener → Placeholder
 * 模块禁用顺序：反序（Placeholder → Listener → Command → Database → Config）
 */
public final class PluginBootstrap {

    private static DraCaveTags plugin;
    private static StartupContext context;
    private static ModuleManager modules;

    private PluginBootstrap() {
    }

    public static void start(@NotNull DraCaveTags owner) {
        plugin = owner;
        context = new StartupContext(owner);
        modules = new ModuleManager(owner);
        registerModules();
        modules.enableAll();
    }

    public static void shutdown() {
        if (modules != null) {
            modules.disableAll();
        }
        context = null;
        modules = null;
    }

    public static DraCaveTags plugin() {
        return plugin;
    }

    public static StartupContext context() {
        return context;
    }

    public static ModuleManager modules() {
        return modules;
    }

    private static void registerModules() {
        modules.register(new ConfigModule(context));
        modules.register(new DatabaseModule(context));
        modules.register(new CommandModule(context));
        modules.register(new ListenerModule(context));
        modules.register(new PlaceholderModule(context));
    }
}
