package com.dracave.tags.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SoundUtil {
    private static final Map<String, Sound> SOUND_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> MISSING_CACHE = new ConcurrentHashMap<>();

    private SoundUtil() {
    }

    public static void play(Player player, String soundName, float volume, float pitch) {
        if (soundName == null || soundName.isBlank()) {
            return;
        }
        String name = soundName.trim();
        if (name.contains(":")) {
            player.playSound(player.getLocation(), name, volume, pitch);
            return;
        }
        Sound sound = SOUND_CACHE.get(name);
        if (sound == null) {
            if (MISSING_CACHE.getOrDefault(name, false)) {
                return;
            }
            try {
                sound = Sound.valueOf(name.toUpperCase(Locale.ROOT));
                SOUND_CACHE.put(name, sound);
            } catch (IllegalArgumentException ex) {
                MISSING_CACHE.put(name, true);
                return;
            }
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public static void play(Player player, String primary, String fallback, float volume, float pitch) {
        if (primary != null && !primary.isBlank() && !isMissing(primary)) {
            play(player, primary, volume, pitch);
        } else if (fallback != null && !fallback.isBlank()) {
            play(player, fallback, volume, pitch);
        }
    }

    private static boolean isMissing(String name) {
        if (name.contains(":")) {
            return false;
        }
        if (SOUND_CACHE.containsKey(name)) {
            return false;
        }
        return MISSING_CACHE.getOrDefault(name, false) || !exists(name);
    }

    private static boolean exists(String name) {
        try {
            Sound sound = Sound.valueOf(name.toUpperCase(Locale.ROOT));
            SOUND_CACHE.put(name, sound);
            return true;
        } catch (IllegalArgumentException e) {
            MISSING_CACHE.put(name, true);
            return false;
        }
    }

    public static void clearCaches() {
        SOUND_CACHE.clear();
        MISSING_CACHE.clear();
    }
}
