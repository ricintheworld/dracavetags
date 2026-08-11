package com.dracave.tags.handlers;

import java.util.List;

public record DCTag(
        String id,
        String display,
        List<String> description,
        String icon,
        int order,
        boolean defaultUnlocked,
        String permission,
        DCTagAnim animation,
        DCTagOffer purchaseOffer,
        List<String> colors,
        boolean shopHidden,
        List<DCTagPotion> potionEffects,
        DCTagPart particle,
        int revision
) {
    public DCTag {
        description = List.copyOf(description);
        colors = List.copyOf(colors);
        potionEffects = List.copyOf(potionEffects);
        permission = permission == null ? "" : permission;
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }

    public DCTag(String id, String display, List<String> description, String icon, int order,
                 boolean defaultUnlocked, String permission, DCTagAnim animation,
                 DCTagOffer purchaseOffer, List<String> colors, boolean shopHidden,
                 List<DCTagPotion> potionEffects, DCTagPart particle) {
        this(id, display, description, icon, order, defaultUnlocked, permission, animation, purchaseOffer,
                colors, shopHidden, potionEffects, particle, 0);
    }

    public DCTag(String id, String display, List<String> description, String icon, int order,
                 boolean defaultUnlocked, String permission, DCTagAnim animation, DCTagOffer purchaseOffer) {
        this(id, display, description, icon, order, defaultUnlocked, permission, animation, purchaseOffer,
                animation == null ? List.of() : animation.colors(),
                purchaseOffer == null, List.of(), null, 0);
    }

    public DCTag(String id, String display, List<String> description, String icon, int order,
                 boolean defaultUnlocked, String permission, DCTagAnim animation) {
        this(id, display, description, icon, order, defaultUnlocked, permission, animation, null);
    }

    public boolean animated() {
        return animation != null;
    }

    public boolean purchasable() {
        return purchaseOffer != null;
    }

    public DCTag withRevision(int revision) {
        return new DCTag(id, display, description, icon, order, defaultUnlocked, permission, animation,
                purchaseOffer, colors, shopHidden, potionEffects, particle, revision);
    }

    public DCTag withOffer(DCTagOffer offer, boolean hidden) {
        return new DCTag(id, display, description, icon, order, defaultUnlocked, permission, animation,
                offer, colors, hidden, potionEffects, particle, revision);
    }
}
