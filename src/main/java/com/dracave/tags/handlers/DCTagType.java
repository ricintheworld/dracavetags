package com.dracave.tags.handlers;

public enum DCTagType {
    STATIC,
    FLOWING_GRADIENT,
    TEXT_FRAMES,
    RAINBOW,
    FLASHING_COLORS;

    public boolean animated() {
        return this != STATIC;
    }

    public boolean dynamic() {
        return this != STATIC;
    }

    public static DCTagType parse(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(java.util.Locale.ROOT).replace("-", "_")) {
            case "static", "静态" -> STATIC;
            case "gradient", "flowing", "渐变" -> FLOWING_GRADIENT;
            case "frames", "frame", "帧" -> TEXT_FRAMES;
            case "rainbow", "彩虹" -> RAINBOW;
            case "flash", "flashing", "闪烁" -> FLASHING_COLORS;
            default -> null;
        };
    }
}
