package com.dracave.tags.handlers;

import java.util.List;

public record CustomDraft(
        String text,
        DCTagType type,
        List<String> colors,
        List<String> frames,
        int periodTicks,
        String icon
) {
    public CustomDraft {
        colors = List.copyOf(colors);
        frames = List.copyOf(frames);
    }

    public static CustomDraft staticTag(String text, String color, String icon) {
        return new CustomDraft(text, DCTagType.STATIC,
                color == null || color.isBlank() ? List.of() : List.of(color), List.of(), 40, icon);
    }

    public static CustomDraft gradient(String text, List<String> colors, int periodTicks, String icon) {
        return new CustomDraft(text, DCTagType.FLOWING_GRADIENT, colors, List.of(), periodTicks, icon);
    }

    public static CustomDraft rainbow(String text, int periodTicks, String icon) {
        return new CustomDraft(text, DCTagType.RAINBOW, List.of(), List.of(), periodTicks, icon);
    }

    public static CustomDraft flash(String text, List<String> colors, int periodTicks, String icon) {
        return new CustomDraft(text, DCTagType.FLASHING_COLORS, colors, List.of(), periodTicks, icon);
    }

    public static CustomDraft frames(String text, List<String> frames, int periodTicks, String icon) {
        return new CustomDraft(text, DCTagType.TEXT_FRAMES, List.of(), frames, periodTicks, icon);
    }
}
