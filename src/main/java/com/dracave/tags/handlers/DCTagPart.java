package com.dracave.tags.handlers;

import java.util.List;
import java.util.Objects;

public record DCTagPart(String particleType, String particleId, List<String> colors) {
    public DCTagPart {
        Objects.requireNonNull(particleType, "particleType");
        if (particleId == null || particleId.isBlank()) {
            particleId = null;
        }
        colors = List.copyOf(colors);
        if (colors.size() > 3) {
            throw new IllegalArgumentException("particle supports at most three colors");
        }
    }

    public static DCTagPart of(String particleType, String particleId, List<String> colors) {
        return new DCTagPart(particleType, particleId, colors);
    }

    public boolean colored() {
        return !colors.isEmpty();
    }
}
