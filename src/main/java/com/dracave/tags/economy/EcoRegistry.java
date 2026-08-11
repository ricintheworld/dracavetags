package com.dracave.tags.economy;

import com.dracave.tags.handlers.EcoType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EcoRegistry {
    private final Map<EcoType, EcoProvider> providers = new ConcurrentHashMap<>();

    public void register(EcoProvider provider) {
        providers.put(provider.type(), provider);
    }

    public EcoProvider get(EcoType type) {
        return providers.get(type);
    }
}
