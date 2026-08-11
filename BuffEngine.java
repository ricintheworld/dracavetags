package com.dracave.tags.engine;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.handlers.DCTag;
import com.dracave.tags.handlers.DCTagPotion;
import com.dracave.tags.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BuffEngine implements AutoCloseable {
    private final DraCaveTags plugin;
    private final DCTagEngine tags;
    private final NamespacedKey snapshotKey;
    private final Set<UUID> reconciling = ConcurrentHashMap.newKeySet();

    public BuffEngine(DraCaveTags plugin, DCTagEngine tags) {
        this.plugin = plugin;
        this.tags = tags;
        this.snapshotKey = new NamespacedKey(plugin, "potion_snapshots");
    }

    public void reconcile(UUID playerId) {
        if (!Bukkit.isPrimaryThread()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                SchedulerUtil.runTaskEntity(player, plugin, () -> reconcile(playerId));
            }
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || !reconciling.add(playerId)) {
            return;
        }
        try {
            restoreManaged(player);
            DCTag equipped = tags.equipped(playerId);
            if (equipped != null) {
                apply(player, equipped.potionEffects());
            }
        } finally {
            reconciling.remove(playerId);
        }
    }

    public boolean isReconciling(UUID playerId) {
        return reconciling.contains(playerId);
    }

    public void release(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            SchedulerUtil.runTaskEntity(player, plugin, () -> release(player));
            return;
        }
        if (reconciling.add(player.getUniqueId())) {
            try {
                restoreManaged(player);
            } finally {
                reconciling.remove(player.getUniqueId());
            }
        }
    }

    private void apply(Player player, List<DCTagPotion> configured) {
        List<Snapshot> snapshots = new ArrayList<>();
        for (DCTagPotion configuredEffect : configured) {
            PotionEffectType type = PotionEffectType.getByName(configuredEffect.effectType());
            if (type == null) {
                continue;
            }
            int amplifier = configuredEffect.level() - 1;
            PotionEffect current = player.getPotionEffect(type);
            if (current == null || current.getAmplifier() < amplifier) {
                snapshots.add(new Snapshot(type.getName(), amplifier,
                        current == null ? null : Original.from(current), System.currentTimeMillis()));
                player.addPotionEffect(new PotionEffect(type, -1, amplifier, true, false, false), true);
            }
        }
        write(player, snapshots);
    }

    private void restoreManaged(Player player) {
        for (Snapshot snapshot : read(player)) {
            PotionEffectType type = PotionEffectType.getByName(snapshot.type);
            if (type == null) {
                continue;
            }
            PotionEffect current = player.getPotionEffect(type);
            boolean stillOurs = current != null && current.getAmplifier() == snapshot.appliedAmplifier && current.isInfinite();
            if (stillOurs) {
                player.removePotionEffect(type);
                PotionEffect original = snapshot.original == null ? null : snapshot.original.restore(type, snapshot.capturedAt);
                if (original != null) {
                    player.addPotionEffect(original, true);
                }
            }
        }
        player.getPersistentDataContainer().remove(snapshotKey);
    }

    private void write(Player player, List<Snapshot> snapshots) {
        if (snapshots.isEmpty()) {
            player.getPersistentDataContainer().remove(snapshotKey);
        } else {
            String value = snapshots.stream().map(Snapshot::serialize).reduce((left, right) -> left + ";" + right).orElse("");
            player.getPersistentDataContainer().set(snapshotKey, PersistentDataType.STRING, value);
        }
    }

    private List<Snapshot> read(Player player) {
        String value = player.getPersistentDataContainer().get(snapshotKey, PersistentDataType.STRING);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Snapshot> result = new ArrayList<>();
        for (String entry : value.split(";")) {
            try {
                result.add(Snapshot.parse(entry));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("忽略损坏的称号药水快照: " + ex.getMessage());
            }
        }
        return result;
    }

    @Override
    public void close() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            release(player);
        }
    }

    private record Original(int amplifier, int duration, boolean ambient, boolean particles, boolean icon) {
        private static Original from(PotionEffect effect) {
            return new Original(effect.getAmplifier(), effect.getDuration(),
                    effect.isAmbient(), effect.hasParticles(), effect.hasIcon());
        }
        private String serialize() {
            return amplifier + "," + duration + "," + ambient + "," + particles + "," + icon;
        }
        private static Original parse(String[] parts, int offset) {
            if (parts.length < offset + 5) {
                throw new IllegalArgumentException("原效果字段数量不足");
            }
            return new Original(Integer.parseInt(parts[offset]), Integer.parseInt(parts[offset + 1]),
                    Boolean.parseBoolean(parts[offset + 2]), Boolean.parseBoolean(parts[offset + 3]),
                    Boolean.parseBoolean(parts[offset + 4]));
        }
        private PotionEffect restore(PotionEffectType type, long capturedAt) {
            int remaining = duration;
            if (duration != -1) {
                long elapsedTicks = Math.max(0L, (System.currentTimeMillis() - capturedAt) / 50L);
                remaining = (int) Math.max(0L, (long) duration - elapsedTicks);
            }
            return remaining <= 0 ? null : new PotionEffect(type, remaining, amplifier, ambient, particles, icon);
        }
    }

    private record Snapshot(String type, int appliedAmplifier, Original original, long capturedAt) {
        private String serialize() {
            return type + "," + appliedAmplifier + "," + capturedAt + "," + (original == null ? "-" : original.serialize());
        }
        private static Snapshot parse(String value) {
            String[] parts = value.split(",", -1);
            if (parts.length < 4) {
                throw new IllegalArgumentException("字段数量不足");
            }
            Original original = parts[3].equals("-") ? null : Original.parse(parts, 3);
            return new Snapshot(parts[0].toUpperCase(Locale.ROOT), Integer.parseInt(parts[1]), original, Long.parseLong(parts[2]));
        }
    }
}
