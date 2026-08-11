package com.dracave.tags.screen;

import java.util.HashMap;
import java.util.Map;

public final class RefreshCache {
    private final Map<Integer, String> lastRendered = new HashMap<>();

    public boolean checkAndUpdate(int slot, String currentRender) {
        String last = lastRendered.get(slot);
        if (last == null || !last.equals(currentRender)) {
            lastRendered.put(slot, currentRender);
            return true;
        }
        return false;
    }

    public void clear() {
        lastRendered.clear();
    }
}
