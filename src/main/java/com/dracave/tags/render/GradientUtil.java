package com.dracave.tags.render;

public final class GradientUtil {
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private GradientUtil() {
    }

    public static double phaseForCycle(double cycle, com.dracave.tags.handlers.DCTagAnim.GradientStyle style) {
        if (style == com.dracave.tags.handlers.DCTagAnim.GradientStyle.PINGPONG) {
            return cycle < 0.5 ? cycle * 2.0 : (1.0 - cycle) * 2.0;
        }
        return cycle;
    }

    public static String lerpClosed(java.util.List<String> colors, double phase) {
        int segments = colors.size();
        double pos = phase * segments;
        int idx = (int) Math.floor(pos);
        if (idx >= segments) {
            idx = segments - 1;
        }
        double t = pos - idx;
        String from = colors.get(idx);
        String to = colors.get((idx + 1) % segments);
        return lerpHex(from, to, t);
    }

    public static String lerpHex(String a, String b, double t) {
        char[] out = new char[7];
        out[0] = '#';
        for (int channel = 0; channel < 3; channel++) {
            int start = 1 + channel * 2;
            int from = Integer.parseInt(a, start, start + 2, 16);
            int to = Integer.parseInt(b, start, start + 2, 16);
            int value = (int) Math.round(from + (to - from) * t);
            out[start] = HEX_DIGITS[(value >> 4) & 0xF];
            out[start + 1] = HEX_DIGITS[value & 0xF];
        }
        return new String(out);
    }

    public static String hex(int r, int g, int b) {
        return new String(new char[]{'#',
                HEX_DIGITS[(r >> 4) & 0xF], HEX_DIGITS[r & 0xF],
                HEX_DIGITS[(g >> 4) & 0xF], HEX_DIGITS[g & 0xF],
                HEX_DIGITS[(b >> 4) & 0xF], HEX_DIGITS[b & 0xF]});
    }
}
