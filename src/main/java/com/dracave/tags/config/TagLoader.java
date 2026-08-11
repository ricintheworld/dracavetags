package com.dracave.tags.config;

import com.dracave.tags.handlers.DCTag;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class TagLoader {
    private static final String YML_EXT = ".yml";

    private final JavaPlugin plugin;
    private final DCTagYamlLoader loader;
    private final DCTagYamlWriter writer;
    private final File tagsFolder;

    public TagLoader(JavaPlugin plugin, DCTagYamlLoader loader, DCTagYamlWriter writer) {
        this.plugin = plugin;
        this.loader = loader;
        this.writer = writer;
        this.tagsFolder = new File(plugin.getDataFolder(), "tags");
    }

    public File tagsFolder() {
        return tagsFolder;
    }

    public boolean hasTags() {
        File[] files = listYmlFiles();
        return files != null && files.length > 0;
    }

    public List<DCTag> loadAll() {
        if (!tagsFolder.exists()) {
            return List.of();
        }
        File[] files = listYmlFiles();
        if (files == null || files.length == 0) {
            return List.of();
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        List<DCTag> definitions = new ArrayList<>();
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - YML_EXT.length());
            DCTagYamlLoader.ParseResult result = loader.parseSingle(id, file);
            definitions.addAll(result.definitions());
            for (String error : result.errors()) {
                plugin.getLogger().warning("标签文件解析错误: " + error);
            }
        }
        return definitions;
    }

    public void writeAll(List<DCTag> definitions) {
        if (!tagsFolder.exists()) {
            tagsFolder.mkdirs();
        }
        File[] oldFiles = listYmlFiles();
        if (oldFiles != null) {
            for (File file : oldFiles) {
                if (!file.delete()) {
                    plugin.getLogger().warning("无法删除旧标签文件: " + file.getName());
                }
            }
        }
        for (DCTag def : definitions) {
            String fileName = def.id().toLowerCase(Locale.ROOT) + YML_EXT;
            File file = new File(tagsFolder, fileName);
            try {
                writer.writeSingle(def, file);
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("写入标签文件失败 " + fileName + ": " + ex.getMessage());
            }
        }
        plugin.getLogger().info("已拆分 " + definitions.size() + " 个称号到 tags/ 文件夹");
    }

    private File[] listYmlFiles() {
        return tagsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(YML_EXT));
    }
}
