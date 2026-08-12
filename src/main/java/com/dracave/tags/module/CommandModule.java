package com.dracave.tags.module;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.bootstrap.StartupContext;
import com.dracave.tags.cmd.DCTagCommand;
import com.dracave.tags.migrate.MigrateCommand;
import org.jetbrains.annotations.NotNull;

/**
 * 命令注册模块。注册 dracavetags 与 ttt 命令。
 * 命令在降级模式下仍注册，由命令内部判断服务是否可用。
 */
public final class CommandModule implements Module {

    private final StartupContext context;

    public CommandModule(@NotNull StartupContext context) {
        this.context = context;
    }

    @Override
    public void enable() {
        DraCaveTags plugin = context.plugin();
        DCTagCommand command = new DCTagCommand(plugin);
        if (plugin.getCommand("dracavetags") != null) {
            plugin.getCommand("dracavetags").setExecutor(command);
            plugin.getCommand("dracavetags").setTabCompleter(command);
        } else {
            plugin.getLogger().severe("未找到 dracavetags 命令定义，请检查 plugin.yml");
        }
        if (plugin.getCommand("ttt") != null) {
            MigrateCommand migrate = new MigrateCommand(plugin);
            plugin.getCommand("ttt").setExecutor(migrate);
            plugin.getCommand("ttt").setTabCompleter(migrate);
        }
    }

    @Override
    public void disable() {
    }
}
