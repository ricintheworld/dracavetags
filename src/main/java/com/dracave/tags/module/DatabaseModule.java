package com.dracave.tags.module;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.api.TagsAPI;
import com.dracave.tags.bootstrap.StartupContext;
import com.dracave.tags.config.Cfg;
import com.dracave.tags.config.DCTagRegistry;
import com.dracave.tags.config.DCTagYamlLoader;
import com.dracave.tags.config.DCTagYamlWriter;
import com.dracave.tags.config.Locale;
import com.dracave.tags.config.TagLoader;
import com.dracave.tags.economy.CoinEco;
import com.dracave.tags.economy.EcoProvider;
import com.dracave.tags.economy.EcoRegistry;
import com.dracave.tags.economy.PointsEco;
import com.dracave.tags.economy.VaultEco;
import com.dracave.tags.engine.BuffEngine;
import com.dracave.tags.engine.CardEngine;
import com.dracave.tags.engine.ChatColorEngine;
import com.dracave.tags.engine.CustomEngine;
import com.dracave.tags.engine.DCTagDefEngine;
import com.dracave.tags.engine.DCTagEngine;
import com.dracave.tags.engine.ParticleEngine;
import com.dracave.tags.engine.RewardEngine;
import com.dracave.tags.engine.ShopEngine;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.EcoType;
import com.dracave.tags.panel.AdminConsole;
import com.dracave.tags.storage.CoinStore;
import com.dracave.tags.storage.ChatColorStore;
import com.dracave.tags.storage.CustomDCTagStore;
import com.dracave.tags.storage.DbPool;
import com.dracave.tags.storage.DCTagStore;
import com.dracave.tags.storage.PlayerStore;
import com.dracave.tags.storage.QuotaStore;
import com.dracave.tags.storage.RewardStore;
import com.dracave.tags.sync.SyncBus;
import com.dracave.tags.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库与业务装配模块。创建连接池、各 Store、全部 Engine、AdminConsole、SyncBus，
 * 注入效果/奖励/跨服钩子，加载定义与自定义标签，启动定时任务。
 * <p>
 * 逻辑完全来自 2.0.0 DraCaveTags.onEnable 的 try 块及后续在线玩家加载、
 * 跨服同步、购买恢复、粒子启动，仅拆分到模块。
 */
public final class DatabaseModule implements Module {

    private final StartupContext context;
    private final Map<UUID, Long> lastPurgeAt = new ConcurrentHashMap<>();

    // 持有引用以便 disable 时关停
    private DbPool database;
    private DCTagEngine tagEngine;
    private ShopEngine shopEngine;
    private CustomEngine customEngine;
    private DCTagDefEngine defEngine;
    private AdminConsole adminConsole;
    private BuffEngine buffEngine;
    private ParticleEngine particleEngine;
    private RewardEngine rewardEngine;
    private ChatColorEngine chatColorEngine;
    private SyncBus syncBus;

    public DatabaseModule(@NotNull StartupContext context) {
        this.context = context;
    }

    @Override
    public void enable() throws Exception {
        DraCaveTags plugin = context.plugin();
        DCTagRegistry registry = context.get(DCTagRegistry.class);
        Locale messages = context.get(Locale.class);

        try {
            database = new DbPool(plugin.getConfig(), plugin.getDataFolder());
            plugin.getLogger().info("数据库连接成功（" + (database.sqliteMode() ? "SQLite" : "MySQL") + "）");

            DCTagStore defStore = new DCTagStore(database);
            TagLoader tagLoader = new TagLoader(plugin, new DCTagYamlLoader(), new DCTagYamlWriter());
            List<DCTag> definitions = tagLoader.loadAll();
            if (definitions.isEmpty()) {
                definitions = defStore.loadAll();
                if (!definitions.isEmpty()) {
                    tagLoader.writeAll(definitions);
                    plugin.getLogger().info("已从数据库加载 " + definitions.size() + " 个称号并生成标签文件");
                } else {
                    plugin.getLogger().warning("尚无全局称号，请确认 tags.yml 后执行 /dctags upload all");
                }
            } else {
                plugin.getLogger().info("已从标签文件加载 " + definitions.size() + " 个称号");
            }
            registry.replaceConfigured(definitions);

            defEngine = new DCTagDefEngine(plugin, defStore, registry, tagLoader);
            PlayerStore playerStore = new PlayerStore(database);
            tagEngine = new DCTagEngine(plugin, registry, playerStore);
            ChatColorStore chatColorStore = new ChatColorStore(database);
            chatColorEngine = new ChatColorEngine(plugin, chatColorStore, tagEngine);
            buffEngine = new BuffEngine(plugin, tagEngine);
            particleEngine = new ParticleEngine(plugin, tagEngine);
            tagEngine.setEffectReconciler(playerId -> {
                buffEngine.reconcile(playerId);
                particleEngine.reconcile(playerId);
            });

            CoinStore coinStore = new CoinStore(database);
            TagsAPI.bindCoin(coinStore);
            QuotaStore quotaStore = new QuotaStore(database);
            RewardStore rewardStore = new RewardStore(database);
            customEngine = new CustomEngine(plugin,
                    new CustomDCTagStore(database), quotaStore, registry, tagEngine);
            customEngine.loadAll();
            TagsAPI.bindCustomTitles(customEngine);

            EcoRegistry currencies = new EcoRegistry();
            currencies.register(new VaultEco());
            currencies.register(new PointsEco());
            currencies.register(new CoinEco(coinStore));
            shopEngine = new ShopEngine(plugin, registry, tagEngine, playerStore, currencies);
            logCurrencyStatus(plugin, currencies, registry, shopEngine);

            rewardEngine = new RewardEngine(plugin, tagEngine, rewardStore, currencies);
            tagEngine.setRewardChecker(rewardEngine::check);
            CardEngine cardEngine = new CardEngine(plugin);
            adminConsole = new AdminConsole(plugin);
            syncBus = new SyncBus(plugin, playerStore);
            TagsAPI.bind(tagEngine, registry, shopEngine);

            context.bind(DbPool.class, database);
            context.bind(DCTagStore.class, defStore);
            context.bind(DCTagDefEngine.class, defEngine);
            context.bind(DCTagEngine.class, tagEngine);
            context.bind(BuffEngine.class, buffEngine);
            context.bind(ParticleEngine.class, particleEngine);
            context.bind(CoinStore.class, coinStore);
            context.bind(QuotaStore.class, quotaStore);
            context.bind(RewardStore.class, rewardStore);
            context.bind(CustomEngine.class, customEngine);
            context.bind(EcoRegistry.class, currencies);
            context.bind(ShopEngine.class, shopEngine);
            context.bind(RewardEngine.class, rewardEngine);
            context.bind(CardEngine.class, cardEngine);
            context.bind(AdminConsole.class, adminConsole);
            context.bind(SyncBus.class, syncBus);
            context.bind(PlayerStore.class, playerStore);
            context.bind(ChatColorStore.class, chatColorStore);
            context.bind(ChatColorEngine.class, chatColorEngine);
            context.bind(TagLoader.class, tagLoader);

            // 加载在线玩家
            for (Player player : Bukkit.getOnlinePlayers()) {
                tagEngine.load(player.getUniqueId());
                chatColorEngine.load(player.getUniqueId());
            }
            startCrossServerSynchronization(plugin, tagEngine, defEngine, syncBus);
            shopEngine.recoverInterruptedPurchases();
            particleEngine.start();

            plugin.getLogger().info("DraCaveTags 已启用（称号仓库/商店/自定义称号，兼容 Paper 1.21+）");
        } catch (Exception ex) {
            plugin.getLogger().severe("称号服务初始化失败，本次以降级模式运行（命令仅提示服务不可用）: " + ex.getMessage());
            plugin.getLogger().severe("请检查 config.yml 的 database 配置后重启服务器");
            shutdownServices();
            throw ex;
        }
    }

    @Override
    public void disable() {
        shutdownServices();
    }

    private void shutdownServices() {
        TagsAPI.unbind();
        if (syncBus != null) {
            syncBus.stop();
        }
        if (shopEngine != null) {
            shopEngine.close();
        }
        if (defEngine != null) {
            defEngine.close();
        }
        if (adminConsole != null) {
            adminConsole.close();
        }
        if (buffEngine != null) {
            buffEngine.close();
        }
        if (particleEngine != null) {
            particleEngine.stop();
        }
        if (rewardEngine != null) {
            rewardEngine.close();
        }
        if (customEngine != null) {
            customEngine.close();
        }
        if (tagEngine != null) {
            tagEngine.close();
        }
        if (database != null) {
            database.close();
        }
        syncBus = null;
        shopEngine = null;
        defEngine = null;
        adminConsole = null;
        buffEngine = null;
        particleEngine = null;
        rewardEngine = null;
        customEngine = null;
        tagEngine = null;
        database = null;
    }

    private void logCurrencyStatus(DraCaveTags plugin, EcoRegistry currencies,
                                   DCTagRegistry registry, ShopEngine shopEngine) {
        for (EcoType type : EcoType.values()) {
            EcoProvider provider = currencies.get(type);
            if (provider != null) {
                plugin.getLogger().info("货币 " + type.id() + "：" + (provider.available() ? "可用" : "不可用"));
            }
        }
        Map<EcoType, List<String>> broken = new EnumMap<>(EcoType.class);
        for (DCTag title : registry.all()) {
            if (title.purchasable() && !shopEngine.currencyAvailable(title.purchaseOffer())) {
                broken.computeIfAbsent(title.purchaseOffer().currency(), key -> new ArrayList<>()).add(title.id());
            }
        }
        broken.forEach((type, ids) -> plugin.getLogger().warning(
                "货币 " + type.id() + " 不可用，以下称号无法购买：" + String.join("、", ids)));
    }

    private void startCrossServerSynchronization(DraCaveTags plugin, DCTagEngine tagEngine,
                                                  DCTagDefEngine defEngine, SyncBus syncBus) {
        if (syncBus != null) {
            syncBus.start();
        }
        long interval = Math.max(20L, plugin.getConfig().getLong(Cfg.DB_SYNC_INTERVAL, 40L));
        SchedulerUtil.runTaskTimerAsynchronously(plugin, () -> {
            if (tagEngine == null) {
                return;
            }
            List<UUID> playerIds = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
            tagEngine.synchronizeEquipped(playerIds);
        }, interval, interval);
        SchedulerUtil.runTaskTimer(plugin, () -> {
            if (defEngine != null) {
                defEngine.refreshIfChanged();
            }
        }, 200L, 200L);
        SchedulerUtil.runTaskTimerAsynchronously(plugin, () -> {
            if (tagEngine == null) {
                return;
            }
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID pid = player.getUniqueId();
                Long last = lastPurgeAt.get(pid);
                if (last != null && now - last < 10000L) {
                    continue;
                }
                lastPurgeAt.put(pid, now);
                tagEngine.purgeExpired(pid);
            }
        }, 200L, 200L);
    }

}