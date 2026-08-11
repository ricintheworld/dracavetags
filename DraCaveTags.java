package com.dracave.tags;

import com.dracave.tags.api.TagsAPI;
import com.dracave.tags.cmd.DCTagCommand;
import com.dracave.tags.config.Cfg;
import com.dracave.tags.config.DCTagRegistry;
import com.dracave.tags.config.DCTagYamlLoader;
import com.dracave.tags.config.DCTagYamlWriter;
import com.dracave.tags.config.GuiConfig;
import com.dracave.tags.config.Locale;
import com.dracave.tags.config.TagLoader;
import com.dracave.tags.economy.CoinEco;
import com.dracave.tags.economy.EcoProvider;
import com.dracave.tags.economy.EcoRegistry;
import com.dracave.tags.economy.PointsEco;
import com.dracave.tags.economy.VaultEco;
import com.dracave.tags.engine.BuffEngine;
import com.dracave.tags.engine.CardEngine;
import com.dracave.tags.engine.ChatPrompt;
import com.dracave.tags.engine.CustomEngine;
import com.dracave.tags.engine.DCTagDefEngine;
import com.dracave.tags.engine.DCTagEngine;
import com.dracave.tags.engine.ParticleEngine;
import com.dracave.tags.engine.RewardEngine;
import com.dracave.tags.engine.ShopEngine;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.EcoType;
import com.dracave.tags.hook.TagsExpansion;
import com.dracave.tags.listen.CardListener;
import com.dracave.tags.listen.ChatListener;
import com.dracave.tags.listen.DCTagListener;
import com.dracave.tags.migrate.MigrateCommand;
import com.dracave.tags.panel.AdminConsole;
import com.dracave.tags.render.DCTagRenderer;
import com.dracave.tags.screen.ScreenListener;
import com.dracave.tags.screen.ScreenSound;
import com.dracave.tags.storage.CoinStore;
import com.dracave.tags.storage.CustomDCTagStore;
import com.dracave.tags.storage.DCTagStore;
import com.dracave.tags.storage.DbPool;
import com.dracave.tags.storage.PlayerStore;
import com.dracave.tags.storage.QuotaStore;
import com.dracave.tags.storage.RewardStore;
import com.dracave.tags.sync.SyncBus;
import com.dracave.tags.util.SchedulerUtil;
import com.dracave.tags.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DraCaveTags extends JavaPlugin {
    private DCTagRegistry registry;
    private Locale messages;
    private DbPool database;
    private DCTagEngine tagEngine;
    private ShopEngine shopEngine;
    private EcoRegistry currencies;
    private CustomEngine customEngine;
    private TagsExpansion expansion;
    private DCTagStore defStore;
    private DCTagDefEngine defEngine;
    private AdminConsole adminConsole;
    private BuffEngine buffEngine;
    private ParticleEngine particleEngine;
    private RewardEngine rewardEngine;
    private CoinStore coinStore;
    private QuotaStore quotaStore;
    private RewardStore rewardStore;
    private CardEngine cardEngine;
    private ChatPrompt chatPrompt;
    private PlayerStore playerStore;
    private TagLoader tagLoader;
    private ScreenSound screenSound;
    private GuiConfig guiConfig;
    private SyncBus syncBus;
    private final Map<UUID, Long> lastPurgeAt = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("tags.yml");
        saveGuiFiles();
        File tagsDir = new File(getDataFolder(), "tags");
        if (!tagsDir.exists()) {
            tagsDir.mkdirs();
        }
        messages = new Locale(this);
        screenSound = new ScreenSound(getConfig());
        guiConfig = new GuiConfig(new File(getDataFolder(), "gui"));
        applyRenderSettings();
        registry = new DCTagRegistry(this);
        chatPrompt = new ChatPrompt(this);
        try {
            database = new DbPool(getConfig(), getDataFolder());
            getLogger().info("数据库连接成功（" + (database.sqliteMode() ? "SQLite" : "MySQL") + "）");
            defStore = new DCTagStore(database);
            tagLoader = new TagLoader(this, new DCTagYamlLoader(), new DCTagYamlWriter());
            List<DCTag> definitions = tagLoader.loadAll();
            if (definitions.isEmpty()) {
                definitions = defStore.loadAll();
                if (!definitions.isEmpty()) {
                    tagLoader.writeAll(definitions);
                    getLogger().info("已从数据库加载 " + definitions.size() + " 个称号并生成标签文件");
                } else {
                    getLogger().warning("尚无全局称号，请确认 tags.yml 后执行 /dctags upload all");
                }
            } else {
                getLogger().info("已从标签文件加载 " + definitions.size() + " 个称号");
            }
            registry.replaceConfigured(definitions);
            defEngine = new DCTagDefEngine(this, defStore, registry, tagLoader);
            playerStore = new PlayerStore(database);
            tagEngine = new DCTagEngine(this, registry, playerStore);
            buffEngine = new BuffEngine(this, tagEngine);
            particleEngine = new ParticleEngine(this, tagEngine);
            tagEngine.setEffectReconciler(playerId -> {
                buffEngine.reconcile(playerId);
                particleEngine.reconcile(playerId);
            });
            coinStore = new CoinStore(database);
            TagsAPI.bindCoin(coinStore);
            quotaStore = new QuotaStore(database);
            rewardStore = new RewardStore(database);
            customEngine = new CustomEngine(this,
                    new CustomDCTagStore(database), quotaStore, registry, tagEngine);
            customEngine.loadAll();
            TagsAPI.bindCustomTitles(customEngine);
            currencies = new EcoRegistry();
            currencies.register(new VaultEco());
            currencies.register(new PointsEco());
            currencies.register(new CoinEco(coinStore));
            shopEngine = new ShopEngine(this, registry, tagEngine, playerStore, currencies);
            logCurrencyStatus();
            rewardEngine = new RewardEngine(this, tagEngine, rewardStore, currencies);
            tagEngine.setRewardChecker(rewardEngine::check);
            cardEngine = new CardEngine(this);
            adminConsole = new AdminConsole(this);
            syncBus = new SyncBus(this, playerStore);
            TagsAPI.bind(tagEngine, registry, shopEngine);
        } catch (Exception ex) {
            getLogger().severe("称号服务初始化失败，本次以降级模式运行（命令仅提示服务不可用）: " + ex.getMessage());
            getLogger().severe("请检查 config.yml 的 database 配置后重启服务器");
            shutdownServices();
        }
        registerCommand();
        getServer().getPluginManager().registerEvents(new ScreenListener(this), this);
        getServer().getPluginManager().registerEvents(new DCTagListener(this), this);
        getServer().getPluginManager().registerEvents(new CardListener(this), this);
        getServer().getPluginManager().registerEvents(chatPrompt, this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        if (tagEngine != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                tagEngine.load(player.getUniqueId());
            }
            startCrossServerSynchronization();
            shopEngine.recoverInterruptedPurchases();
            particleEngine.start();
            registerPlaceholderApi();
            getLogger().info("DraCaveTags 已启用（称号仓库/商店/自定义称号，兼容 Paper 1.21+）");
        } else {
            getLogger().warning("DraCaveTags 处于降级模式：修复数据库配置后重启服务器即可恢复全部功能");
        }
    }

    private void logCurrencyStatus() {
        for (EcoType type : EcoType.values()) {
            EcoProvider provider = currencies.get(type);
            if (provider != null) {
                getLogger().info("货币 " + type.id() + "：" + (provider.available() ? "可用" : "不可用"));
            }
        }
        Map<EcoType, List<String>> broken = new EnumMap<>(EcoType.class);
        for (DCTag title : registry.all()) {
            if (title.purchasable() && !shopEngine.currencyAvailable(title.purchaseOffer())) {
                broken.computeIfAbsent(title.purchaseOffer().currency(), key -> new ArrayList<>()).add(title.id());
            }
        }
        broken.forEach((type, ids) -> getLogger().warning(
                "货币 " + type.id() + " 不可用，以下称号无法购买：" + String.join("、", ids)));
    }

    private void registerCommand() {
        DCTagCommand command = new DCTagCommand(this);
        if (getCommand("dracavetags") != null) {
            getCommand("dracavetags").setExecutor(command);
            getCommand("dracavetags").setTabCompleter(command);
        } else {
            getLogger().severe("未找到 dracavetags 命令定义，请检查 plugin.yml");
        }
        if (getCommand("ttt") != null) {
            MigrateCommand migrate = new MigrateCommand(this);
            getCommand("ttt").setExecutor(migrate);
            getCommand("ttt").setTabCompleter(migrate);
        }
    }

    private void startCrossServerSynchronization() {
        if (syncBus != null) {
            syncBus.start();
        }
        long interval = Math.max(20L, getConfig().getLong(Cfg.DB_SYNC_INTERVAL, 40L));
        SchedulerUtil.runTaskTimerAsynchronously(this, () -> {
            if (tagEngine == null) {
                return;
            }
            List<UUID> playerIds = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
            tagEngine.synchronizeEquipped(playerIds);
        }, interval, interval);
        SchedulerUtil.runTaskTimer(this, () -> {
            if (defEngine != null) {
                defEngine.refreshIfChanged();
            }
        }, 200L, 200L);
        SchedulerUtil.runTaskTimerAsynchronously(this, () -> {
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

    private void registerPlaceholderApi() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new TagsExpansion(this);
            if (expansion.register()) {
                getLogger().info("已注册 PlaceholderAPI 扩展 %dracavetags_*%");
            } else {
                getLogger().warning("PlaceholderAPI 扩展注册失败");
            }
        }
    }

    public void reloadFiles() {
        reloadConfig();
        messages.reload();
        applyRenderSettings();
        SoundUtil.clearCaches();
        guiConfig = new GuiConfig(new File(getDataFolder(), "gui"));
    }

    private void applyRenderSettings() {
        DCTagRenderer.configure(
                getConfig().getInt(Cfg.ANIM_FRAME_STEP, 2),
                getConfig().getInt(Cfg.ANIM_GRADIENT_CHAR_STEP, 1));
    }

    private void saveGuiFiles() {
        File guiDir = new File(getDataFolder(), "gui");
        if (!guiDir.exists()) {
            guiDir.mkdirs();
            for (String name : new String[]{"main.yml", "self.yml", "shop.yml", "custom.yml", "admin.yml", "reward.yml"}) {
                saveResource("gui/" + name, false);
            }
        }
    }

    private void saveResourceIfMissing(String resource) {
        File target = new File(getDataFolder(), resource);
        if (!target.exists()) {
            saveResource(resource, false);
        }
    }

    private void shutdownServices() {
        TagsAPI.unbind();
        if (expansion != null) {
            expansion.unregister();
        }
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
        shopEngine = null;
        tagEngine = null;
        database = null;
    }

    @Override
    public void onDisable() {
        shutdownServices();
        SoundUtil.clearCaches();
    }

    public DCTagRegistry registry() { return registry; }
    public Locale messages() { return messages; }
    public DCTagEngine tagEngine() { return tagEngine; }
    public ShopEngine shopEngine() { return shopEngine; }
    public CustomEngine customEngine() { return customEngine; }
    public DCTagStore defStore() { return defStore; }
    public DCTagDefEngine defEngine() { return defEngine; }
    public AdminConsole adminConsole() { return adminConsole; }
    public BuffEngine buffEngine() { return buffEngine; }
    public ParticleEngine particleEngine() { return particleEngine; }
    public RewardEngine rewardEngine() { return rewardEngine; }
    public CoinStore coinStore() { return coinStore; }
    public QuotaStore quotaStore() { return quotaStore; }
    public RewardStore rewardStore() { return rewardStore; }
    public CardEngine cardEngine() { return cardEngine; }
    public ChatPrompt chatPrompt() { return chatPrompt; }
    public DbPool database() { return database; }
    public PlayerStore playerStore() { return playerStore; }
    public TagLoader tagLoader() { return tagLoader; }
    public ScreenSound screenSound() { return screenSound; }
    public GuiConfig guiConfig() { return guiConfig; }
    public EcoRegistry currencies() { return currencies; }
}
