package com.dracave.tags.render;

import com.dracave.tags.handlers.DCTagAnim;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

public final class FlowRenderer {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private FlowRenderer() {
    }

    public static String render(String display, List<String> colors, double cycle,
                                DCTagAnim.GradientStyle style, int charStep) {
        if (colors.size() < 2) {
            return display;
        }
        int[] points = display.codePoints().toArray();
        int n = points.length;
        int step = Math.max(1, charStep);
        double timePhase = GradientUtil.phaseForCycle(cycle, style);
        StringBuilder result = new StringBuilder(n * 11);
        String last = null;
        for (int i = 0; i < n; i++) {
            double pos = (double) (i - i % step) / (double) n;
            String color = GradientUtil.lerpClosed(colors, (pos + timePhase) % 1.0);
            if (!color.equals(last)) {
                result.append('<').append(color).append('>');
                last = color;
            }
            result.append(MINI.escapeTags(new String(points, i, 1)));
        }
        return result.toString();
    }
}
