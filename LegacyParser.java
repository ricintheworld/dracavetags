package com.dracave.tags.migrate.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LegacyParser {
    private static final Map<Character, String> TRADITIONAL = Map.ofEntries(
            Map.entry('0', "#000000"), Map.entry('1', "#0000AA"), Map.entry('2', "#00AA00"),
            Map.entry('3', "#00AAAA"), Map.entry('4', "#AA0000"), Map.entry('5', "#AA00AA"),
            Map.entry('6', "#FFAA00"), Map.entry('7', "#AAAAAA"), Map.entry('8', "#555555"),
            Map.entry('9', "#5555FF"), Map.entry('a', "#55FF55"), Map.entry('b', "#55FFFF"),
            Map.entry('c', "#FF5555"), Map.entry('d', "#FF55FF"), Map.entry('e', "#FFFF55"),
            Map.entry('f', "#FFFFFF"));

    private LegacyParser() {
    }

    private record Char(char[] codepoint, String color, int mods) {
    }

    private static List<Char> parseChars(String legacy) {
        List<Char> chars = new ArrayList<>();
        String color = null;
        boolean bold = false, obfuscated = false, italic = false, underlined = false, strikethrough = false;
        int i = 0;
        int len = legacy.length();
        while (i < len) {
            char c = legacy.charAt(i);
            if ((c == '&' || c == '\u00A7') && i + 1 < len) {
                char code = Character.toLowerCase(legacy.charAt(i + 1));
                if (code == '#') {
                    if (i + 8 <= len && allHex(legacy, i + 2, 6)) {
                        color = "#" + legacy.substring(i + 2, i + 8).toUpperCase(Locale.ROOT);
                        bold = obfuscated = italic = underlined = strikethrough = false;
                        i += 8;
                        continue;
                    }
                    chars.add(new Char(new char[]{c}, color, mods(bold, obfuscated, italic, underlined, strikethrough)));
                    i += 2;
                    continue;
                }
                if (code == 'x') {
                    if (i + 13 < len && legacy.charAt(i + 2) == c && isHex(legacy.charAt(i + 3))
                            && legacy.charAt(i + 4) == c && isHex(legacy.charAt(i + 5))
                            && legacy.charAt(i + 6) == c && isHex(legacy.charAt(i + 7))
                            && legacy.charAt(i + 8) == c && isHex(legacy.charAt(i + 9))
                            && legacy.charAt(i + 10) == c && isHex(legacy.charAt(i + 11))
                            && legacy.charAt(i + 12) == c && isHex(legacy.charAt(i + 13))) {
                        String hex = "" + legacy.charAt(i + 3) + legacy.charAt(i + 5) + legacy.charAt(i + 7)
                                + legacy.charAt(i + 9) + legacy.charAt(i + 11) + legacy.charAt(i + 13);
                        color = "#" + hex.toUpperCase(Locale.ROOT);
                        bold = obfuscated = italic = underlined = strikethrough = false;
                        i += 14;
                        continue;
                    }
                    if (i + 8 <= len && allHex(legacy, i + 2, 6)) {
                        color = "#" + legacy.substring(i + 2, i + 8).toUpperCase(Locale.ROOT);
                        bold = obfuscated = italic = underlined = strikethrough = false;
                        i += 8;
                        continue;
                    }
                    chars.add(new Char(new char[]{c}, color, mods(bold, obfuscated, italic, underlined, strikethrough)));
                    i += 2;
                    continue;
                }
                switch (code) {
                    case '&', '\u00A7' -> {
                        chars.add(new Char(new char[]{c}, color, mods(bold, obfuscated, italic, underlined, strikethrough)));
                        i += 2;
                        continue;
                    }
                    case 'r' -> {
                        color = null;
                        bold = obfuscated = italic = underlined = strikethrough = false;
                        i += 2;
                        continue;
                    }
                    case 'l' -> {
                        bold = true;
                        i += 2;
                        continue;
                    }
                    case 'k' -> {
                        obfuscated = true;
                        i += 2;
                        continue;
                    }
                    case 'o' -> {
                        italic = true;
                        i += 2;
                        continue;
                    }
                    case 'n' -> {
                        underlined = true;
                        i += 2;
                        continue;
                    }
                    case 'm' -> {
                        strikethrough = true;
                        i += 2;
                        continue;
                    }
                    default -> {
                    }
                }
                if (TRADITIONAL.containsKey(code)) {
                    color = TRADITIONAL.get(code);
                    bold = obfuscated = italic = underlined = strikethrough = false;
                    i += 2;
                    continue;
                }
                chars.add(new Char(new char[]{c}, color, mods(bold, obfuscated, italic, underlined, strikethrough)));
                i += 2;
                continue;
            }
            int cp = legacy.codePointAt(i);
            chars.add(new Char(new String(Character.toChars(cp)).toCharArray(), color,
                    mods(bold, obfuscated, italic, underlined, strikethrough)));
            i += Character.charCount(cp);
        }
        return chars;
    }

    private static int mods(boolean bold, boolean obfuscated, boolean italic, boolean underlined, boolean strikethrough) {
        int m = 0;
        if (bold) m |= 1;
        if (obfuscated) m |= 2;
        if (italic) m |= 4;
        if (underlined) m |= 8;
        if (strikethrough) m |= 16;
        return m;
    }

    public static String toMiniMessage(String legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return "";
        }
        List<Char> chars = parseChars(legacy);
        List<String> colors = distinctColors(chars);
        if (colors.size() >= 2 && isSmoothGradient(colors)) {
            return renderGradient(chars, colors);
        }
        return renderStatic(chars);
    }

    private static List<String> distinctColors(List<Char> chars) {
        List<String> colors = new ArrayList<>();
        for (Char ch : chars) {
            if (ch.color() != null && (colors.isEmpty() || !colors.get(colors.size() - 1).equals(ch.color()))) {
                colors.add(ch.color());
            }
        }
        return colors;
    }

    private static boolean isSmoothGradient(List<String> colors) {
        Integer prev = null;
        for (String col : colors) {
            int rgb = Integer.parseInt(col.substring(1), 16);
            if (prev != null) {
                int dR = ((rgb >> 16) & 0xFF) - ((prev >> 16) & 0xFF);
                int dG = ((rgb >> 8) & 0xFF) - ((prev >> 8) & 0xFF);
                int dB = (rgb & 0xFF) - (prev & 0xFF);
                if (Math.sqrt(dR * dR + dG * dG + dB * dB) > 90.0) {
                    return false;
                }
            }
            prev = rgb;
        }
        return true;
    }

    private static String gradientStops(List<String> colors) {
        List<String> stops = new ArrayList<>();
        for (String col : colors) {
            if (stops.isEmpty() || !stops.get(stops.size() - 1).equals(col)) {
                stops.add(col);
            }
        }
        if (stops.size() <= 1) {
            return stops.isEmpty() ? "#FFFFFF" : stops.get(0);
        }
        return stops.get(0) + ":" + stops.get(stops.size() - 1);
    }

    private static String renderGradient(List<Char> chars, List<String> colors) {
        StringBuilder out = new StringBuilder();
        out.append("<gradient:").append(gradientStops(colors)).append('>');
        int lastMods = -1;
        for (Char ch : chars) {
            if (ch.mods() != lastMods) {
                if (lastMods != -1) {
                    out.append(closeMods(lastMods));
                }
                out.append(openMods(ch.mods()));
                lastMods = ch.mods();
            }
            out.append(escape(new String(ch.codepoint())));
        }
        if (lastMods != -1) {
            out.append(closeMods(lastMods));
        }
        out.append("</gradient>");
        return out.toString();
    }

    private static String renderStatic(List<Char> chars) {
        StringBuilder out = new StringBuilder();
        StringBuilder segText = new StringBuilder();
        String segColor = null;
        int segMods = -1;
        boolean first = true;
        for (Char ch : chars) {
            if (first) {
                segColor = ch.color();
                segMods = ch.mods();
                first = false;
            } else if (!java.util.Objects.equals(segColor, ch.color()) || segMods != ch.mods()) {
                flush(out, segText, segColor, segMods);
                segColor = ch.color();
                segMods = ch.mods();
            }
            segText.append(new String(ch.codepoint()));
        }
        flush(out, segText, segColor, segMods);
        return out.toString();
    }

    private static void flush(StringBuilder out, StringBuilder segText, String segColor, int segMods) {
        if (segText.length() == 0) {
            return;
        }
        String text = escape(segText.toString());
        if (segColor != null) {
            out.append(openMods(segMods)).append('<').append(segColor).append('>')
                    .append(text).append("</").append(segColor).append('>').append(closeMods(segMods));
        } else {
            out.append(openMods(segMods)).append(text).append(closeMods(segMods));
        }
        segText.setLength(0);
    }

    private static String openMods(int mods) {
        StringBuilder sb = new StringBuilder();
        if ((mods & 1) != 0) sb.append("<bold>");
        if ((mods & 2) != 0) sb.append("<obfuscated>");
        if ((mods & 4) != 0) sb.append("<italic>");
        if ((mods & 8) != 0) sb.append("<underlined>");
        if ((mods & 16) != 0) sb.append("<strikethrough>");
        return sb.toString();
    }

    private static String closeMods(int mods) {
        StringBuilder sb = new StringBuilder();
        if ((mods & 16) != 0) sb.append("</strikethrough>");
        if ((mods & 8) != 0) sb.append("</underlined>");
        if ((mods & 4) != 0) sb.append("</italic>");
        if ((mods & 2) != 0) sb.append("</obfuscated>");
        if ((mods & 1) != 0) sb.append("</bold>");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("<", "\\<").replace(">", "\\>");
    }

    public record ParsedTitle(String text, List<String> colors, int charCount) {
        public ParsedTitle {
            colors = List.copyOf(colors);
        }
    }

    public static ParsedTitle parse(String legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return new ParsedTitle("", List.of(), 0);
        }
        List<Char> chars = parseChars(legacy);
        StringBuilder text = new StringBuilder();
        for (Char ch : chars) {
            text.append(new String(ch.codepoint()));
        }
        return new ParsedTitle(text.toString(), distinctColors(chars), chars.size());
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean allHex(String s, int start, int count) {
        if (start + count > s.length()) {
            return false;
        }
        for (int k = start; k < start + count; k++) {
            if (!isHex(s.charAt(k))) {
                return false;
            }
        }
        return true;
    }
}
