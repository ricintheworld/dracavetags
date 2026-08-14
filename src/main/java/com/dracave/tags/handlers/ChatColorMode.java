package com.dracave.tags.handlers;

public enum ChatColorMode {
    TITLE,
    CUSTOM,
    DEFAULT;

    public static ChatColorMode parse(String value) {
        if (value == null) {
            return TITLE;
        }
        try {
            return valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TITLE;
        }
    }
}