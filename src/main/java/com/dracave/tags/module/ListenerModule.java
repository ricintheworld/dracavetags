package com.dracave.tags.module;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.bootstrap.StartupContext;
import com.dracave.tags.engine.ChatPrompt;
import com.dracave.tags.hook.TrChatHook;
import com.dracave.tags.listen.CardListener;
import com.dracave.tags.listen.ChatListener;
import com.dracave.tags.listen.DCTagListener;
import com.dracave.tags.screen.ScreenListener;
import org.jetbrains.annotations.NotNull;

/**
 * 监听器注册模块。注册 GUI、标签、卡片、聊天输入、聊天事件监听器。
 * 监听器在降级模式下仍注册，由各监听器内部判断服务是否可用。
 */
public final class ListenerModule implements Module {

    private final StartupContext context;

    public ListenerModule(@NotNull StartupContext context) {
        this.context = context;
    }

    @Override
    public void enable() {
        DraCaveTags plugin = context.plugin();
        plugin.getServer().getPluginManager().registerEvents(new ScreenListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new DCTagListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new CardListener(plugin), plugin);
        ChatPrompt chatPrompt = context.getOrNull(ChatPrompt.class);
        if (chatPrompt != null) {
            plugin.getServer().getPluginManager().registerEvents(chatPrompt, plugin);
        }
        plugin.getServer().getPluginManager().registerEvents(new ChatListener(plugin), plugin);
        TrChatHook.register(plugin);
    }

    @Override
    public void disable() {
    }
}
