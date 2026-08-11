package com.dracave.tags.migrate.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UuidResolver {
    public static final long PERPETUAL_THRESHOLD = 4_733_481_600_000L;
    private static final Pattern JSON_ENTRY = Pattern.compile(
            "\\{\\s*\"uuid\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]+)\"");

    private final Map<String, UUID> uuids = new HashMap<>();

    public void add(String name, String uuid) {
        try {
            uuids.putIfAbsent(name, UUID.fromString(uuid));
        } catch (IllegalArgumentException ignored) {
        }
    }

    public void addOrOverride(String name, String uuid) {
        try {
            uuids.put(name, UUID.fromString(uuid));
        } catch (IllegalArgumentException ignored) {
        }
    }

    public void addAllOrOverride(Map<String, String> map) {
        if (map == null) {
            return;
        }
        for (var entry : map.entrySet()) {
            addOrOverride(entry.getKey(), entry.getValue());
        }
    }

    public UUID get(String name) {
        return uuids.get(name);
    }

    public boolean isEmpty() {
        return uuids.isEmpty();
    }

    public int size() {
        return uuids.size();
    }

    public static void loadLocal(UuidResolver resolver, File serverFolder) {
        if (serverFolder == null || !serverFolder.isDirectory()) {
            return;
        }
        loadJsonFiles(resolver, serverFolder);
        loadXConomy(resolver, serverFolder);
        loadLuckPerms(resolver, serverFolder);
    }

    private static void loadJsonFiles(UuidResolver resolver, File serverFolder) {
        for (String name : new String[]{"usercache.json", "whitelist.json", "ops.json", "banned-players.json"}) {
            File f = new File(serverFolder, name);
            if (!f.isFile()) {
                continue;
            }
            try {
                String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                Matcher m = JSON_ENTRY.matcher(content);
                while (m.find()) {
                    resolver.add(m.group(2), m.group(1));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void loadXConomy(UuidResolver resolver, File serverFolder) {
        File db = new File(serverFolder, "plugins/XConomy/playerdata/data.db");
        if (!db.isFile()) {
            return;
        }
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT UID, player FROM xconomy")) {
                while (rs.next()) {
                    resolver.add(rs.getString(2), rs.getString(1));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void loadLuckPerms(UuidResolver resolver, File serverFolder) {
        File dir = new File(serverFolder, "plugins/LuckPerms");
        if (!dir.isDirectory()) {
            return;
        }
        File[] dbs = dir.listFiles((d, name) -> name.endsWith(".mv.db"));
        if (dbs == null) {
            return;
        }
        for (File db : dbs) {
            try {
                Class.forName("org.h2.Driver");
                try (Connection c = DriverManager.getConnection("jdbc:h2:" + db.getAbsolutePath().replace(".mv.db", ""), "sa", "");
                     Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT uuid, username FROM luckperms_users")) {
                    while (rs.next()) {
                        resolver.add(rs.getString(2), rs.getString(1));
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
}
