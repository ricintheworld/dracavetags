package com.dracave.tags.engine;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.DCTagPart;
import com.dracave.tags.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ParticleEngine {
    private final DraCaveTags plugin;
    private final DCTagEngine tags;
    private final Map<UUID, DCTagPart> active = new ConcurrentHashMap<>();
    private SchedulerUtil.Task particleTask;

    public ParticleEngine(DraCaveTags plugin, DCTagEngine tags) {
        this.plugin = plugin;
        this.tags = tags;
    }

    public void reconcile(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            active.remove(playerId);
            return;
        }
        DCTag equipped = tags.equipped(playerId);
        DCTagPart particle = equipped == null ? null : equipped.particle();
        if (particle == null) {
            active.remove(playerId);
        } else {
            active.put(playerId, particle);
        }
    }

    public void start() {
        if (particleTask != null) {
            return;
        }
        particleTask = SchedulerUtil.runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, DCTagPart> entry : active.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) {
                    continue;
                }
                DCTagPart config = entry.getValue();
                player.getScheduler().run(plugin, task -> spawn(player, config), null);
            }
        }, 5L, 5L);
    }

    public void stop() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        active.clear();
    }

    private void spawn(Player player, DCTagPart config) {
        try {
            Particle particle = Particle.valueOf(config.particleType().toUpperCase(Locale.ROOT));
            Location location = player.getLocation().add(0, 1.1, 0);
            switch (particle) {
                case DUST -> {
                    Color color = color(config, 0);
                    player.spawnParticle(particle, location, 1, 0.35, 0.35, 0.35, new Particle.DustOptions(color, 1.0F));
                }
                case DUST_COLOR_TRANSITION -> {
                    Color from = color(config, 0);
                    Color to = color(config, 1);
                    player.spawnParticle(particle, location, 1, 0.35, 0.35, 0.35,
                            new Particle.DustTransition(from, to, 1.0F));
                }
                default -> player.spawnParticle(particle, location, 1, 0.35, 0.35, 0.35, 0.01);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("粒子特效无效 " + config.particleType() + ": " + ex.getMessage());
            active.remove(player.getUniqueId());
        }
    }

    private Color color(DCTagPart config, int index) {
        if (config.colors().size() > index) {
            try {
                String hex = config.colors().get(index).replace("#", "");
                return Color.fromRGB(Integer.parseInt(hex, 16));
            } catch (NumberFormatException ignored) {
            }
        }
        return Color.WHITE;
    }

    public static boolean validParticle(String particleType) {
        if (particleType == null || particleType.isBlank()) {
            return false;
        }
        try {
            Particle.valueOf(particleType.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
