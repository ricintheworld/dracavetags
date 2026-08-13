package com.dracave.tags.migrate;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.migrate.core.MigrateConfig;
import com.dracave.tags.migrate.core.Migrator;
import com.dracave.tags.migrate.core.TitleData;
import com.dracave.tags.migrate.core.UuidResolver;
import com.dracave.tags.util.SchedulerUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MigrateCommand implements CommandExecutor, TabCompleter {
    private final DraCaveTags plugin;

    public MigrateCommand(DraCaveTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dracave.tags.migrate") && !sender.hasPermission("ttt.use")) {
            sender.sendMessage("§c你没有权限使用此命令");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/ttt title null [源库] —— 静态迁移（渐变/粗体/乱码直写数据库）");
            sender.sendMessage("§e/ttt title color [源库] —— 动态渐变迁移（生成 tags.yml 可 upload）");
            sender.sendMessage("§e/ttt db [源库] —— 迁移玩家数据（UUID 本地获取，不联网）");
            sender.sendMessage("§e/ttt old --check —— 检查旧版 DraCaveTitle 数据");
            sender.sendMessage("§e/ttt old db —— 迁移旧版 DraCaveTitle 玩家数据");
            sender.sendMessage("§e/ttt old title —— 迁移旧版 DraCaveTitle 称号数据");
            return true;
        }
        final MigrateConfig.Scope scope;
        final MigrateConfig.Mode mode;
        String modeArg = "";
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "title" -> {
                scope = MigrateConfig.Scope.TITLES;
                if (args.length < 2) {
                    sender.sendMessage("§e用法: /ttt title <null|color> [源库]");
                    return true;
                }
                modeArg = args[1].toLowerCase(Locale.ROOT);
                mode = "color".equals(modeArg) || "dynamic".equals(modeArg)
                        ? MigrateConfig.Mode.DYNAMIC : MigrateConfig.Mode.STATIC;
                runMigrate(sender, args, scope, mode);
                return true;
            }
            case "db" -> {
                scope = MigrateConfig.Scope.DATA;
                mode = MigrateConfig.Mode.STATIC;
                runMigrate(sender, args, scope, mode);
                return true;
            }
            case "old" -> {
                handleOld(sender, args);
                return true;
            }
            default -> {
                sender.sendMessage("§c未知命令: " + args[0] + "（用 title、db 或 old）");
                return true;
            }
        }
    }

    private void runMigrate(CommandSender sender, String[] args, MigrateConfig.Scope scope, MigrateConfig.Mode mode) {
        int pathIndex = scope == MigrateConfig.Scope.TITLES ? 2 : 1;
        String sourcePath = "plugins/PlayerTitle/PlayerTitle.db";
        for (int i = pathIndex; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) {
                sourcePath = a;
            }
        }
        File sourceDb = new File(sourcePath);
        if (!sourceDb.isFile()) {
            sender.sendMessage("§c源库不存在: " + sourceDb.getAbsolutePath());
            return;
        }
        File serverFolder = plugin.getDataFolder().getParentFile().getParentFile();
        // 子目录部署回溯：若当前推算的 serverFolder 没有 usercache.json，向上查找最多 4 层
        serverFolder = locateServerRoot(serverFolder);
        sender.sendMessage("§7服务器根目录: " + serverFolder.getAbsolutePath());
        UuidResolver resolver = new UuidResolver();
        // 打印各 UUID 源的可用性
        for (String f : new String[]{"usercache.json", "whitelist.json", "ops.json", "banned-players.json"}) {
            File jf = new File(serverFolder, f);
            sender.sendMessage("§7  " + f + ": " + (jf.isFile() ? "§a存在 (" + (jf.length() / 1024) + " KB)" : "§c不存在"));
        }
        File xconomy = new File(serverFolder, "plugins/XConomy/playerdata/data.db");
        sender.sendMessage("§7  XConomy: " + (xconomy.isFile() ? "§a存在" : "§c不存在"));
        File luckperms = new File(serverFolder, "plugins/LuckPerms");
        sender.sendMessage("§7  LuckPerms: " + (luckperms.isDirectory() ? "§a存在" : "§c不存在"));
        UuidResolver.loadLocal(resolver, serverFolder);
        sender.sendMessage("§7外部 UUID 源解析: " + resolver.size() + " 个玩家");
        if (scope == MigrateConfig.Scope.DATA && resolver.isEmpty()) {
            sender.sendMessage("§e外部 UUID 源不可用，将尝试从源库 player_uuid 列获取…");
        }
        File dataFolder = plugin.getDataFolder();
        MigrateConfig.TargetDb target = MigrateConfig.fromDraCaveConfig(
                new File(dataFolder, "config.yml").getAbsolutePath(), dataFolder);
        MigrateConfig config = MigrateConfig.builder()
                .source("jdbc:sqlite:" + sourceDb.getAbsolutePath(), "", "")
                .target(target.url(), target.user(), target.password(), target.prefix())
                .dryRun(false)
                .scope(scope)
                .mode(mode)
                .backup(new File(plugin.getDataFolder(), "backup"), target.sqliteFile())
                .titlesYml(new File(dataFolder, "tags.yml"))
                .build();
        String what = scope == MigrateConfig.Scope.TITLES
                ? (mode == MigrateConfig.Mode.DYNAMIC ? "动态渐变定义（color）" : "静态定义（null）")
                : "玩家数据";
        sender.sendMessage("§e开始迁移" + what + "，UUID 本地获取不联网…");
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            TitleData.MigrationReport report = null;
            String fail = null;
            try {
                Migrator migrator = new Migrator(config);
                migrator.setResolver(resolver);
                report = migrator.run();
            } catch (Exception ex) {
                fail = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            }
            final TitleData.MigrationReport result = report;
            final String error = fail;
            SchedulerUtil.runTask(plugin, () -> {
                if (error != null) {
                    sender.sendMessage("§c迁移失败: " + error);
                    return;
                }
                sender.sendMessage("§a迁移完成: " + result.summary());
                plugin.getLogger().info("迁移报告: " + result.summary());
                if (scope == MigrateConfig.Scope.TITLES) {
                    sender.sendMessage("§e已生成 tags.yml，执行 /dctags upload 导入定义（静态/动态按所选模式生效）");
                }
            });
        });
    }

    private void handleOld(CommandSender sender, String[] args) {
        String sub = args.length < 2 ? "--check" : args[1].toLowerCase(Locale.ROOT);
        File oldPlugin = new File(plugin.getDataFolder().getParentFile(), "DraCaveTitle");
        File oldDb = new File(oldPlugin, "data.db");
        File oldTitles = new File(oldPlugin, "titles.yml");

        if ("--check".equals(sub) || "check".equals(sub)) {
            sender.sendMessage("§6=== DraCaveTitle 迁移检查 ===");
            boolean ok = true;
            if (oldPlugin.exists() && oldPlugin.isDirectory()) {
                sender.sendMessage("§a✓ 旧版插件目录: plugins/DraCaveTitle");
            } else { sender.sendMessage("§c✗ 未找到旧版插件目录"); ok = false; }
            if (oldDb.exists()) {
                sender.sendMessage("§a✓ 旧版数据库: " + (oldDb.length() / 1024) + " KB");
            } else { sender.sendMessage("§c✗ 未找到旧版数据库 (data.db)"); ok = false; }
            if (oldTitles.exists()) {
                sender.sendMessage("§a✓ 旧版 titles.yml");
            } else { sender.sendMessage("§e! 未找到 titles.yml（可用 ttt old title --db 从数据库提取）"); }
            if (ok) sender.sendMessage("§6就绪。执行 /ttt old db 迁移玩家数据，/ttt old title 迁移称号");
        } else if ("db".equals(sub)) {
            if (!oldDb.exists()) { sender.sendMessage("§c旧版数据库不存在"); return; }
            sender.sendMessage("§e迁移玩家数据中…");
            SchedulerUtil.runTaskAsynchronously(plugin, () -> {
                try {
                    int n = migrateOldDb(oldDb, oldPlugin);
                    SchedulerUtil.runTask(plugin, () -> sender.sendMessage("§a迁移完成: " + n + " 条记录"));
                } catch (Exception e) {
                    SchedulerUtil.runTask(plugin, () -> sender.sendMessage("§c失败: " + e.getMessage()));
                }
            });
        } else if ("title".equals(sub)) {
            boolean fromDb = args.length > 2 && "--db".equals(args[2]);
            if (fromDb || !oldTitles.exists()) {
                if (!oldDb.exists()) { sender.sendMessage("§c旧版数据库不存在"); return; }
                SchedulerUtil.runTaskAsynchronously(plugin, () -> {
                    try {
                        String yml = migrateTagsFromDb(oldDb, oldPlugin);
                        SchedulerUtil.runTask(plugin, () -> {
                            sender.sendMessage("§a已生成 tags.yml（从数据库提取），执行 /dctags upload all");
                            sender.sendMessage(yml);
                        });
                    } catch (Exception e) {
                        SchedulerUtil.runTask(plugin, () -> sender.sendMessage("§c失败: " + e.getMessage()));
                    }
                });
            } else {
                try {
                    java.nio.file.Files.copy(oldTitles.toPath(),
                            new File(plugin.getDataFolder(), "tags.yml").toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    sender.sendMessage("§a已将 titles.yml 复制为 tags.yml，执行 /dctags upload all");
                } catch (Exception e) { sender.sendMessage("§c复制失败: " + e.getMessage()); }
            }
        } else {
            sender.sendMessage("§e/ttt old --check  检查旧版数据");
            sender.sendMessage("§e/ttt old db       迁移玩家数据");
            sender.sendMessage("§e/ttt old title    迁移称号定义");
        }
    }

    private int migrateOldDb(File oldDb, File folder) throws Exception {
        java.sql.Connection oc = java.sql.DriverManager.getConnection("jdbc:sqlite:" + oldDb.getAbsolutePath());
        int n = 0;
        try (oc) {
            java.sql.Connection nc = plugin.database().source().getConnection();
            try (nc) { nc.setAutoCommit(false);
                Map<String, String> mapping = Map.of(
                    "player", plugin.database().playerTbl(),
                    "unlock", plugin.database().unlockTbl(),
                    "coin", plugin.database().coinTbl(),
                    "quota", plugin.database().quotaTbl(),
                    "reward", plugin.database().rewardTbl()
                );
                for (Map.Entry<String, String> e : mapping.entrySet()) {
                    String oldTable = "dracavetitle_" + e.getKey();
                    String newTable = e.getValue();
                    try (java.sql.Statement st = oc.createStatement()) {
                        if (!tableExists(oc, oldTable)) continue;
                        java.sql.ResultSet rs = st.executeQuery("SELECT * FROM " + oldTable);
                        int cols = rs.getMetaData().getColumnCount();
                        StringBuilder ph = new StringBuilder();
                        for (int i = 0; i < cols; i++) { if (i > 0) ph.append(","); ph.append("?"); }
                        try (java.sql.PreparedStatement ps = nc.prepareStatement(
                                "INSERT OR IGNORE INTO \"" + newTable + "\" VALUES (" + ph + ")")) {
                            while (rs.next()) {
                                for (int i = 1; i <= cols; i++) ps.setObject(i, rs.getObject(i));
                                ps.addBatch(); n++;
                            }
                            ps.executeBatch();
                        }
                    } catch (Exception ex) {
                        plugin.getLogger().warning("迁移表 " + oldTable + " 失败: " + ex.getMessage());
                    }
                }
                nc.commit();
            }
        }
        return n;
    }

    private boolean tableExists(java.sql.Connection conn, String table) {
        try (java.sql.ResultSet rs = conn.getMetaData().getTables(null, null, table, null)) {
            return rs.next();
        } catch (Exception e) { return false; }
    }

    private String migrateTagsFromDb(File oldDb, File folder) throws Exception {
        java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + oldDb.getAbsolutePath());
        StringBuilder yml = new StringBuilder("tags:\n");
        try (c) {
            if (!tableExists(c, "dracavetitle_tag_definition")) {
                return "tags: {}\n# 旧版数据库中没有称号定义表";
            }
            java.sql.Statement st = c.createStatement();
            java.sql.ResultSet rs = st.executeQuery("SELECT * FROM dracavetitle_tag_definition ORDER BY display_order");
            while (rs.next()) {
                String id = rs.getString("id");
                String text = rs.getString("display_text");
                yml.append("  ").append(id != null ? id : "unknown").append(":\n");
                yml.append("    text: \"").append(text != null ? text : "").append("\"\n");
                yml.append("    icon: NAME_TAG\n    order: ").append(rs.getInt("display_order")).append("\n");
            }
        }
        String out = yml.toString();
        java.nio.file.Files.writeString(new File(plugin.getDataFolder(), "tags.yml").toPath(), out);
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return filter(List.of("title", "db", "old"), args[0]);
        }
        if (args.length == 2) {
            if ("title".equalsIgnoreCase(args[0])) return filter(List.of("null", "color"), args[1]);
            if ("old".equalsIgnoreCase(args[0])) return filter(List.of("--check", "db", "title"), args[1]);
        }
        if (args.length == 3 && "old".equalsIgnoreCase(args[0]) && "title".equalsIgnoreCase(args[1])) {
            return filter(List.of("--db"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        if (prefix == null || prefix.isEmpty()) return options;
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    /**
     * 定位真正的服务器根目录。标准部署下就是 plugins 的父目录；
     * 某些子目录部署（如 server/Paper/plugins）需要向上回溯找到含 usercache.json 的目录。
     */
    private File locateServerRoot(File candidate) {
        if (candidate == null) return new File(".");
        // 若当前目录有 usercache.json 或 plugins 子目录，直接返回
        if (new File(candidate, "usercache.json").isFile()
                || new File(candidate, "plugins").isDirectory()) {
            return candidate;
        }
        // 向上最多回溯 4 层
        File parent = candidate;
        for (int i = 0; i < 4 && parent != null; i++) {
            parent = parent.getParentFile();
            if (parent == null) break;
            if (new File(parent, "usercache.json").isFile()
                    || new File(parent, "plugins").isDirectory()) {
                return parent;
            }
        }
        return candidate;
    }
}
