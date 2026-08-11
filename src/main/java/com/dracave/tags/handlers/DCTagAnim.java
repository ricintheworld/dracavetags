package com.dracave.tags.handlers;

import java.util.List;
import java.util.Locale;

public record DCTagAnim(AnimType type, List<String> colors, List<String> frames, int periodTicks, GradientStyle style) {
    public DCTagAnim {
        if (type == null) {
            throw new IllegalArgumentException("animation type is required");
        }
        colors = List.copyOf(colors);
        frames = List.copyOf(frames);
        if (periodTicks < 1) {
            throw new IllegalArgumentException("periodTicks must be positive");
        }
        if ((type == AnimType.FLOWING_GRADIENT || type == AnimType.SOLID_GRADIENT || type == AnimType.FLASHING_COLORS)
                && colors.size() < 2) {
            throw new IllegalArgumentException("animation requires at least two colors");
        }
        if (type == AnimType.TEXT_FRAMES && frames.size() < 2) {
            throw new IllegalArgumentException("text animation requires at least two frames");
        }
        if (style == null) {
            style = GradientStyle.CYCLE;
        }
    }

    public DCTagAnim(AnimType type, List<String> colors, List<String> frames, int periodTicks) {
        this(type, colors, frames, periodTicks, GradientStyle.CYCLE);
    }

    public DCTagAnim(List<String> colors, int periodTicks) {
        this(AnimType.FLOWING_GRADIENT, colors, List.of(), periodTicks, GradientStyle.CYCLE);
    }

    public DCTagAnim(List<String> colors, int periodTicks, GradientStyle style) {
        this(AnimType.FLOWING_GRADIENT, colors, List.of(), periodTicks, style);
    }

    /** 兼容旧调用：等价于 {@link #style()}。 */
    public GradientStyle mode() {
        return style;
    }

    public static DCTagAnim rainbow(int periodTicks) {
        return new DCTagAnim(AnimType.RAINBOW, List.of(), List.of(), periodTicks);
    }

    public enum AnimType {
        FLOWING_GRADIENT,
        SOLID_GRADIENT,
        TEXT_FRAMES,
        RAINBOW,
        FLASHING_COLORS
    }

    public enum GradientStyle {
        CYCLE,
        PINGPONG;

        public static GradientStyle parse(String value) {
            if (value == null) {
                return CYCLE;
            }
            return switch (value.toLowerCase(Locale.ROOT).replace("-", "_")) {
                case "pingpong", "bounce", "cos", "回弹" -> PINGPONG;
                default -> CYCLE;
            };
        }
    }
}
