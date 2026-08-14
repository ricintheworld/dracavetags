package com.dracave.tags.handlers;

public record ChatColorPreference(ChatColorMode mode, String customColor) {
    public static final ChatColorPreference FOLLOW_TITLE = new ChatColorPreference(ChatColorMode.TITLE, null);

    public ChatColorPreference {
        mode = mode == null ? ChatColorMode.TITLE : mode;
        customColor = customColor == null || customColor.isBlank() ? null : customColor.toUpperCase(java.util.Locale.ROOT);
    }
}