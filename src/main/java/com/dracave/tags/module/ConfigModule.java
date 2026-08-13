package com.dracave.tags.module;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.bootstrap.StartupContext;
import com.dracave.tags.config.Cfg;
import com.dracave.tags.config.DCTagRegistry;
import com.dracave.tags.config.GuiConfig;
import com.dracave.tags.config.Locale;
import com.dracave.tags.config.TagLoader;
import com.dracave.tags.engine.ChatPrompt;
import com.dracave.tags.render.DCTagRenderer;
import com.dracave.tags.screen.ScreenSound;
import org.jetbrains.annotations.NotNull;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 配置模块。负责加载 config.yml、tags.yml、gui/ 目录、消息本地化、
 * 渲染参数、标签注册表与聊天输入处理器。这些是所有后续模块的基础依赖。
 * <p>
 * 逻辑完全来自 2.0.0 DraCaveTags.onEnable 的前半段，仅拆分到模块。
 */
public final class ConfigModule implements Module {

    private final StartupContext context;

    public ConfigModule(@NotNull StartupContext context) {
        this.context = context;
    }

    @Override
    public void enable() {
        DraCaveTags plugin = context.plugin();
        plugin.saveDefaultConfig();
        File tagsFile = new File(plugin.getDataFolder(), "tags.yml");
        if (!tagsFile.exists()) {
            plugin.saveResource("tags.yml", false);
        }
        saveGuiFiles(plugin);
        migrateLegacyAdminMenu(plugin);
        File tagsDir = new File(plugin.getDataFolder(), "tags");
        if (!tagsDir.exists()) {
            tagsDir.mkdirs();
        }

        Locale messages = new Locale(plugin);
        ScreenSound screenSound = new ScreenSound(plugin.getConfig());
        GuiConfig guiConfig = new GuiConfig(new File(plugin.getDataFolder(), "gui"));
        applyRenderSettings(plugin);

        DCTagRegistry registry = new DCTagRegistry(plugin);
        ChatPrompt chatPrompt = new ChatPrompt(plugin);

        context.bind(Locale.class, messages);
        context.bind(ScreenSound.class, screenSound);
        context.bind(GuiConfig.class, guiConfig);
        context.bind(DCTagRegistry.class, registry);
        context.bind(ChatPrompt.class, chatPrompt);
    }

    @Override
    public void disable() {
        // 配置服务无外部资源需释放
    }

    @Override
    public void reload() {
        DraCaveTags plugin = context.plugin();
        plugin.reloadConfig();
        context.getIfPresent(Locale.class).ifPresent(Locale::reload);
        applyRenderSettings(plugin);
        com.dracave.tags.util.SoundUtil.clearCaches();
        GuiConfig guiConfig = new GuiConfig(new File(plugin.getDataFolder(), "gui"));
        context.bind(GuiConfig.class, guiConfig);
    }

    private void applyRenderSettings(DraCaveTags plugin) {
        DCTagRenderer.configure(
                plugin.getConfig().getInt(Cfg.ANIM_FRAME_STEP, 2),
                plugin.getConfig().getInt(Cfg.ANIM_GRADIENT_CHAR_STEP, 1));
    }

    private void saveGuiFiles(DraCaveTags plugin) {
        File guiDir = new File(plugin.getDataFolder(), "gui");
        if (!guiDir.exists()) {
            guiDir.mkdirs();
            for (String name : new String[]{"main.yml", "self.yml", "shop.yml", "custom.yml", "admin.yml", "reward.yml"}) {
                plugin.saveResource("gui/" + name, false);
            }
        }
    }

    private void migrateLegacyAdminMenu(DraCaveTags plugin) {
        File file = new File(plugin.getDataFolder(), "gui" + File.separator + "admin.yml");
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<String> inventory = yaml.getStringList("inventory");
        if (!inventory.contains("上空空传创检空返下")
                || !"<gold>上传 tags.yml".equals(yaml.getString("icons.传.display"))
                || !"<gray>校验 tags.yml".equals(yaml.getString("icons.检.display"))) {
            return;
        }
        inventory.replaceAll(row -> row.equals("上空空传创检空返下") ? "上空空同创空空返下" : row);
        yaml.set("inventory", inventory);
        yaml.set("icons.传", null);
        yaml.set("icons.检", null);
        yaml.set("icons.同.material", "WRITABLE_BOOK");
        yaml.set("icons.同.display", "<gold>同步配置文件");
        yaml.set("icons.同.lore", List.of(
                "<gray>校验并同步 tags.yml 到数据库",
                "<gray>配置有误时不会修改现有称号"));
        yaml.set("icons.同.left", List.of("command dctags upload all"));
        try {
            yaml.save(file);
            plugin.getLogger().info("已将管理菜单的上传/校验按钮合并为同步配置文件");
        } catch (IOException ex) {
            plugin.getLogger().warning("升级管理菜单失败: " + ex.getMessage());
        }
    }
}
