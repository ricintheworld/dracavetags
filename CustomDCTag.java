package com.dracave.tags.handlers;

import java.util.List;
import java.util.UUID;

public record CustomDCTag(
        String id,
        UUID ownerId,
        String text,
        DCTagType type,
        List<String> colors,
        List<String> frames,
        int periodTicks,
        String icon,
        int revision,
        long createdAt,
        long updatedAt
) {
    public CustomDCTag {
        colors = List.copyOf(colors);
        frames = List.copyOf(frames);
    }
}
