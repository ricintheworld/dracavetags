package com.dracave.tags.render;

import com.dracave.tags.handlers.DCTagAnim;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

public final class SolidRenderer {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private SolidRenderer() {
    }

    public static String render(String display, List<String> colors, double cycle, DCTagAnim.GradientStyle style) {
        if (colors.size() < 2) {
            return display;
        }
        double phase = GradientUtil.phaseForCycle(cycle, style);
        String color = GradientUtil.lerpClosed(colors, phase);
        return "<" + color + ">" + MINI.escapeTags(display) + "</" + color + ">";
    }
}
