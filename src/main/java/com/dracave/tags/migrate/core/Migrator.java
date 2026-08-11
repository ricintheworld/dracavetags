package com.dracave.tags.migrate.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Migrator {
    private final MigrateConfig config;
    private final List<String> warnings = new ArrayList<>();
    private final Map<Long, String> idMap = new LinkedHashMap<>();
    private UuidResolver resolver = new UuidResolver();

    public Migrator(MigrateConfig config) {
        this.config = config;
    }

    public void setResolver(UuidResolver resolver) {
        this.resolver = resolver;
    }

    public UuidResolver resolver() {
        return resolver;
    }

    public TitleData.MigrationReport run() throws SQLException, IOException {
        SourceReader reader = new SourceReader(config.sourceJdbcUrl, config.sourceUser, config.sourcePassword);
        if (config.scope == MigrateConfig.Scope.TITLES) {
            List<TitleData.SourceTitle> titles = reader.readTitles();
            List<TitleData.TitleBuff> buffs = reader.readBuffs();
            buildIdMap(titles);
            if (!config.dryRun) {
                backupTarget();
            }
            TargetWriter writer = new TargetWriter(config.targetJdbcUrl, config.targetUser,
                    config.targetPassword, config.tablePrefix);
            return writeTitles(writer, titles, buffs);
        }
        List<TitleData.SourceTitle> titles = reader.readTitles();
        List<TitleData.OwnedTitle> owned = reader.readOwned();
        List<TitleData.CustomQuota> quotas = reader.readQuotas();
        List<TitleData.CoinRow> coins = reader.readCoins();
        Map<String, String> sourceUuids = reader.readSourceUuids();
        if (!sourceUuids.isEmpty()) {
            resolver.addAllOrOverride(sourceUuids);
            warnings.add("从源库提取 " + sourceUuids.size() + " 个玩家 UUID（优先于外部 UUID 源）");
        }
        buildIdMap(titles);
        if (!config.dryRun) {
            backupTarget();
        }
        TargetWriter writer = new TargetWriter(config.targetJdbcUrl, config.targetUser,
                config.targetPassword, config.tablePrefix);
        return writeData(writer, owned, quotas, coins);
    }

    private TitleData.MigrationReport writeTitles(TargetWriter writer, List<TitleData.SourceTitle> titles,
                                                  List<TitleData.TitleBuff> buffs) throws SQLException, IOException {
        writeTitlesYaml(titles, buffs, config.mode);
        if (config.mode == MigrateConfig.Mode.STATIC) {
            warnings.add("定义库已生成（静态 MiniMessage），执行 /dctags upload 导入");
        }
        if (config.dryRun) {
            return new TitleData.MigrationReport(titles.size(), 0, 0, 0,
                    buffs.size(), 0, 0, warnings);
        }
        int[] defs = writer.writeDefinitions(titles, buffs, idMap, config.mode);
        if (defs[1] > 0) {
            warnings.add(defs[1] + " 条已存在定义已覆盖（重新迁移修复污染）");
        }
        return new TitleData.MigrationReport(defs[0], 0, 0, 0,
                buffs.size(), 0, 0, warnings);
    }

    private TitleData.MigrationReport writeData(TargetWriter writer, List<TitleData.OwnedTitle> owned,
                                                List<TitleData.CustomQuota> quotas,
                                                List<TitleData.CoinRow> coins) throws SQLException {
        if (resolver.isEmpty()) {
            throw new IllegalStateException(
                    "UUID 解析器为空：源库无 player_uuid 列，且外部 UUID 源（usercache/XConomy/LuckPerms）均无法读取，无法迁移玩家数据");
        }
        int[] own = writer.writeOwned(owned, idMap, resolver);
        int quotaRows = writer.writeQuotas(quotas, resolver);
        int coinRows = writer.writeCoins(coins, resolver);
        int distinctPlayers = (int) owned.stream().map(TitleData.OwnedTitle::playerName).distinct().count();
        int equippedPlayers = (int) owned.stream().filter(TitleData.OwnedTitle::isUse)
                .map(TitleData.OwnedTitle::playerName).distinct().count();
        if (own[2] > 0) {
            warnings.add(own[2] + " 条拥有记录因找不到玩家 UUID 已跳过");
        }
        return new TitleData.MigrationReport(0, own[0], distinctPlayers, equippedPlayers,
                0, quotaRows, coinRows, warnings);
    }

    private void buildIdMap(List<TitleData.SourceTitle> titles) {
        for (TitleData.SourceTitle t : titles) {
            idMap.put(t.id(), String.valueOf(t.id()));
        }
    }

    private void writeTitlesYaml(List<TitleData.SourceTitle> titles, List<TitleData.TitleBuff> buffs,
                                 MigrateConfig.Mode mode) throws IOException {
        File out = config.titlesYmlFile;
        if (out == null) {
            return;
        }
        if (out.getParentFile() != null) {
            out.getParentFile().mkdirs();
        }
        if (out.isFile()) {
            File old = new File(out.getParentFile(), "tags.yml-old");
            Files.copy(out.toPath(), old.toPath(), StandardCopyOption.REPLACE_EXISTING);
            warnings.add("原定义库已备份为 " + old.getAbsolutePath());
        }
        TitlesYmlWriter.write(out.toPath(), titles, idMap, buffs, mode);
        warnings.add("已生成定义库 " + out.getAbsolutePath());
    }

    private void backupTarget() throws IOException {
        File db = config.targetDbFile;
        if (db == null || !db.isFile()) {
            warnings.add("目标库非文件型（MySQL），跳过文件备份，请自行在数据库侧备份");
            return;
        }
        File dir = config.targetBackupDir != null ? config.targetBackupDir
                : new File(db.getParentFile(), "backup");
        if (!dir.exists() && !dir.mkdirs()) {
            warnings.add("无法创建备份目录: " + dir);
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File backup = new File(dir, "dracavetags-data-" + stamp + ".db");
        Files.copy(db.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        warnings.add("已备份目标库到 " + backup.getAbsolutePath());
    }

    public Map<Long, String> idMap() {
        return idMap;
    }

    public List<String> warnings() {
        return warnings;
    }
}
