package com.dracave.tags.module;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.bootstrap.StartupContext;
import com.dracave.tags.engine.DCTagEngine;
import com.dracave.tags.hook.TagsExpansion;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI 扩展注册模块。仅当数据库模块成功（tagEngine 就绪）
 * 且 PlaceholderAPI 插件存在时注册。
 */
public final class PlaceholderModule implements Module {

    private final StartupContext context;
    private TagsExpansion expansion;

    public PlaceholderModule(@NotNull StartupContext context) {
        this.context = context;
    }

    @Override
    public void enable() {
        DraCaveTags plugin = context.plugin();
        DCTagEngine tagEngine = context.getOrNull(DCTagEngine.class);
        if (tagEngine == null) {
            return;
        }
        if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new TagsExpansion(plugin);
            if (expansion.register()) {
                plugin.getLogger().info("已注册 PlaceholderAPI 扩展 %dracavetags_*%");
            } else {
                plugin.getLogger().warning("PlaceholderAPI 扩展注册失败");
            }
        }
    }

    @Override
    public void disable() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }
}
