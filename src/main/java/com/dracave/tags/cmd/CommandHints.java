package com.dracave.tags.cmd;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class CommandHints {
    private static final Map<String, String> LABELS = new ConcurrentHashMap<>();

    private CommandHints() {
    }

    static String hint(String value, String description) {
        String label = value + "（" + description + "）";
        LABELS.put(label, value);
        return label;
    }

    static String strip(String value) {
        return value == null ? null : LABELS.getOrDefault(value, value);
    }

    static String[] normalize(String[] args) {
        return Arrays.stream(args).map(CommandHints::strip).toArray(String[]::new);
    }
}
