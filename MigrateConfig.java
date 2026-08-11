package com.dracave.tags.migrate.core;

import java.io.File;
import java.nio.file.Files;

public final class MigrateConfig {
    public enum Mode {
        STATIC, DYNAMIC
    }

    public enum Scope {
        TITLES, DATA
    }

    public final String sourceJdbcUrl;
    public final String sourceUser;
    public final String sourcePassword;
    public final String targetJdbcUrl;
    public final String targetUser;
    public final String targetPassword;
    public final String tablePrefix;
    public final boolean includeOnline;
    public final boolean dryRun;
    public final Mode mode;
    public final Scope scope;
    public final File targetBackupDir;
    public final File targetDbFile;
    public final File titlesYmlFile;

    private MigrateConfig(Builder b) {
        this.sourceJdbcUrl = b.sourceJdbcUrl;
        this.sourceUser = b.sourceUser;
        this.sourcePassword = b.sourcePassword;
        this.targetJdbcUrl = b.targetJdbcUrl;
        this.targetUser = b.targetUser;
        this.targetPassword = b.targetPassword;
        this.tablePrefix = b.tablePrefix;
        this.includeOnline = b.includeOnline;
        this.dryRun = b.dryRun;
        this.mode = b.mode;
        this.scope = b.scope;
        this.targetBackupDir = b.targetBackupDir;
        this.targetDbFile = b.targetDbFile;
        this.titlesYmlFile = b.titlesYmlFile;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String sourceJdbcUrl;
        private String sourceUser = "";
        private String sourcePassword = "";
        private String targetJdbcUrl;
        private String targetUser = "";
        private String targetPassword = "";
        private String tablePrefix = "dracavetags_";
        private boolean includeOnline;
        private boolean dryRun = true;
        private Mode mode = Mode.STATIC;
        private Scope scope = Scope.TITLES;
        private File targetBackupDir;
        private File targetDbFile;
        private File titlesYmlFile;

        public Builder source(String url, String user, String password) {
            this.sourceJdbcUrl = url;
            this.sourceUser = user == null ? "" : user;
            this.sourcePassword = password == null ? "" : password;
            return this;
        }

        public Builder target(String url, String user, String password, String prefix) {
            this.targetJdbcUrl = url;
            this.targetUser = user == null ? "" : user;
            this.targetPassword = password == null ? "" : password;
            if (prefix != null && !prefix.isBlank()) {
                this.tablePrefix = prefix;
            }
            return this;
        }

        public Builder includeOnline(boolean v) {
            this.includeOnline = v;
            return this;
        }

        public Builder dryRun(boolean v) {
            this.dryRun = v;
            return this;
        }

        public Builder mode(Mode v) {
            this.mode = v == null ? Mode.STATIC : v;
            return this;
        }

        public Builder scope(Scope v) {
            this.scope = v == null ? Scope.TITLES : v;
            return this;
        }

        public Builder backup(File dir, File dbFile) {
            this.targetBackupDir = dir;
            this.targetDbFile = dbFile;
            return this;
        }

        public Builder titlesYml(File file) {
            this.titlesYmlFile = file;
            return this;
        }

        public MigrateConfig build() {
            return new MigrateConfig(this);
        }
    }

    public static TargetDb fromDraCaveConfig(String configYml, File dataFolder) {
        String type = "SQLITE";
        String host = "127.0.0.1", database = "minecraft", user = "root", password = "";
        int port = 3306;
        String params = "?useSSL=false&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        String prefix = "dracavetags_";
        String sqliteFile = "data.db";
        try {
            for (String line : Files.readAllLines(new File(configYml).toPath())) {
                String t = line.trim();
                String key = t.replaceAll("\\s*#.*$", "");
                if (key.startsWith("type:")) {
                    type = key.substring(5).trim().toUpperCase();
                } else if (key.startsWith("table-prefix:")) {
                    prefix = key.substring(13).trim().replace("\"", "");
                } else if (key.startsWith("file:")) {
                    sqliteFile = key.substring(5).trim().replace("\"", "");
                } else if (key.startsWith("host:")) {
                    host = key.substring(5).trim().replace("\"", "");
                } else if (key.startsWith("port:")) {
                    port = Integer.parseInt(key.substring(5).trim());
                } else if (key.startsWith("database:")) {
                    database = key.substring(9).trim().replace("\"", "");
                } else if (key.startsWith("user:")) {
                    user = key.substring(5).trim().replace("\"", "");
                } else if (key.startsWith("password:")) {
                    password = key.substring(9).trim().replace("\"", "");
                }
            }
        } catch (Exception ignored) {
        }
        if (type.equals("SQLITE")) {
            File db = new File(dataFolder, sqliteFile);
            return new TargetDb("jdbc:sqlite:" + db.getAbsolutePath() + "?busy_timeout=5000", "", "", prefix, db, true);
        }
        return new TargetDb("jdbc:mysql://" + host + ":" + port + "/" + database + params,
                user, password, prefix, null, false);
    }

    public record TargetDb(String url, String user, String password, String prefix, File sqliteFile, boolean isSqlite) {
    }
}
