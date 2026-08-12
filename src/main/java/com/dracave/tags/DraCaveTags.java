package com.dracave.tags;

import com.dracave.tags.bootstrap.PluginBootstrap;
import com.dracave.tags.bootstrap.StartupContext;
import com.dracave.tags.config.DCTagRegistry;
import com.dracave.tags.config.GuiConfig;
import com.dracave.tags.config.Locale;
import com.dracave.tags.config.TagLoader;
import com.dracave.tags.economy.EcoRegistry;
import com.dracave.tags.engine.BuffEngine;
import com.dracave.tags.engine.CardEngine;
import com.dracave.tags.engine.ChatPrompt;
import com.dracave.tags.engine.CustomEngine;
import com.dracave.tags.engine.DCTagDefEngine;
import com.dracave.tags.engine.DCTagEngine;
import com.dracave.tags.engine.ParticleEngine;
import com.dracave.tags.engine.RewardEngine;
import com.dracave.tags.engine.ShopEngine;
import com.dracave.tags.panel.AdminConsole;
import com.dracave.tags.screen.ScreenSound;
import com.dracave.tags.storage.CoinStore;
import com.dracave.tags.storage.DbPool;
import com.dracave.tags.storage.DCTagStore;
import com.dracave.tags.storage.PlayerStore;
import com.dracave.tags.storage.QuotaStore;
import com.dracave.tags.storage.RewardStore;
import com.dracave.tags.util.SoundUtil;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DraCaveTags 2.0.0 主入口。主类仅作为启动器，全部装配交由 {@link PluginBootstrap} 编排。
 * 同时提供 getter 桥接，让 2.0.0 的 Screen/Engine/Command 代码能通过 plugin.xxx() 访问服务。
 */
public final class DraCaveTags extends JavaPlugin {

    @Override
    public void onEnable() {
        PluginBootstrap.start(this);
    }

    @Override
    public void onDisable() {
        PluginBootstrap.shutdown();
        SoundUtil.clearCaches();
    }

    private StartupContext ctx() {
        return PluginBootstrap.context();
    }

    /**
     * 重载所有可热更新的配置。
     */
    public void reloadFiles() {
        if (PluginBootstrap.modules() != null) {
            PluginBootstrap.modules().reloadAll();
        }
    }

    // ---------- getter 桥接（保持 2.0.0 原签名，改为从上下文取） ----------

    public DCTagRegistry registry() {
        return ctx() != null ? ctx().getOrNull(DCTagRegistry.class) : null;
    }

    public Locale messages() {
        return ctx() != null ? ctx().getOrNull(Locale.class) : null;
    }

    public DCTagEngine tagEngine() {
        return ctx() != null ? ctx().getOrNull(DCTagEngine.class) : null;
    }

    public ShopEngine shopEngine() {
        return ctx() != null ? ctx().getOrNull(ShopEngine.class) : null;
    }

    public CustomEngine customEngine() {
        return ctx() != null ? ctx().getOrNull(CustomEngine.class) : null;
    }

    public DCTagStore defStore() {
        return ctx() != null ? ctx().getOrNull(DCTagStore.class) : null;
    }

    public DCTagDefEngine defEngine() {
        return ctx() != null ? ctx().getOrNull(DCTagDefEngine.class) : null;
    }

    public AdminConsole adminConsole() {
        return ctx() != null ? ctx().getOrNull(AdminConsole.class) : null;
    }

    public BuffEngine buffEngine() {
        return ctx() != null ? ctx().getOrNull(BuffEngine.class) : null;
    }

    public ParticleEngine particleEngine() {
        return ctx() != null ? ctx().getOrNull(ParticleEngine.class) : null;
    }

    public RewardEngine rewardEngine() {
        return ctx() != null ? ctx().getOrNull(RewardEngine.class) : null;
    }

    public CoinStore coinStore() {
        return ctx() != null ? ctx().getOrNull(CoinStore.class) : null;
    }

    public QuotaStore quotaStore() {
        return ctx() != null ? ctx().getOrNull(QuotaStore.class) : null;
    }

    public RewardStore rewardStore() {
        return ctx() != null ? ctx().getOrNull(RewardStore.class) : null;
    }

    public CardEngine cardEngine() {
        return ctx() != null ? ctx().getOrNull(CardEngine.class) : null;
    }

    public ChatPrompt chatPrompt() {
        return ctx() != null ? ctx().getOrNull(ChatPrompt.class) : null;
    }

    public DbPool database() {
        return ctx() != null ? ctx().getOrNull(DbPool.class) : null;
    }

    public PlayerStore playerStore() {
        return ctx() != null ? ctx().getOrNull(PlayerStore.class) : null;
    }

    public TagLoader tagLoader() {
        return ctx() != null ? ctx().getOrNull(TagLoader.class) : null;
    }

    public ScreenSound screenSound() {
        return ctx() != null ? ctx().getOrNull(ScreenSound.class) : null;
    }

    public GuiConfig guiConfig() {
        return ctx() != null ? ctx().getOrNull(GuiConfig.class) : null;
    }

    public EcoRegistry currencies() {
        return ctx() != null ? ctx().getOrNull(EcoRegistry.class) : null;
    }
}
