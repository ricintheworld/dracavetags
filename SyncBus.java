package com.dracave.tags.sync;

import com.dracave.tags.DraCaveTags;
import com.dracave.tags.handlers.PlayerData;
import com.dracave.tags.storage.PlayerStore;
import com.dracave.tags.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class SyncBus implements PluginMessageListener {
    private static final String CHANNEL = "dracavetags:sync";
    private static final long POLL_INTERVAL_TICKS = 100L;

    private final DraCaveTags plugin;
    private final PlayerStore playerStore;
    private SchedulerUtil.Task pollTask;
    private boolean enabled;

    public SyncBus(DraCaveTags plugin, PlayerStore playerStore) {
        this.plugin = plugin;
        this.playerStore = playerStore;
    }

    public void start() {
        if (enabled) {
            return;
        }
        enabled = true;
        try {
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        } catch (Exception ex) {
            plugin.getLogger().info("插件消息通道注册失败，将仅使用轮询同步: " + ex.getMessage());
        }
        pollTask = SchedulerUtil.runTaskTimerAsynchronously(plugin, this::pollEquipped, POLL_INTERVAL_TICKS, POLL_INTERVAL_TICKS);
    }

    public void stop() {
        enabled = false;
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
        try {
            Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL);
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        } catch (Exception ignored) {
        }
    }

    public void publishEquip(UUID playerId, String tagId) {
        if (!enabled) {
            return;
        }
        byte[] payload = encode("equip", playerId, tagId);
        if (payload == null) {
            return;
        }
        try {
            Bukkit.getOnlinePlayers().stream().findFirst().ifPresent(p ->
                    p.sendPluginMessage(plugin, CHANNEL, payload));
        } catch (Exception ignored) {
        }
    }

    public void publishUnequip(UUID playerId) {
        if (!enabled) {
            return;
        }
        byte[] payload = encode("unequip", playerId, "");
        if (payload == null) {
            return;
        }
        try {
            Bukkit.getOnlinePlayers().stream().findFirst().ifPresent(p ->
                    p.sendPluginMessage(plugin, CHANNEL, payload));
        } catch (Exception ignored) {
        }
    }

    private void pollEquipped() {
        List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        List<UUID> ids = online.stream().map(Player::getUniqueId).toList();
        try {
            Collection<PlayerStore.EquippedSnap> snaps = playerStore.batchLoadEquipped(ids).values();
            for (PlayerStore.EquippedSnap snap : snaps) {
                PlayerData cached = plugin.tagEngine().getCached(snap.playerId());
                if (cached == null) {
                    continue;
                }
                if (snap.updatedAt() <= plugin.tagEngine().lastLocalWriteAt(snap.playerId())) {
                    continue;
                }
                String current = cached.equippedId();
                String remote = snap.equippedId();
                if ((current == null && remote == null) || (current != null && current.equals(remote))) {
                    continue;
                }
                plugin.tagEngine().applyRemoteEquip(snap.playerId(), remote);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("跨服同步轮询失败: " + ex.getMessage());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String action = in.readUTF();
            UUID playerId = UUID.fromString(in.readUTF());
            String tagId = in.readUTF();
            if (playerId.equals(player.getUniqueId())) {
                return;
            }
            if ("equip".equals(action)) {
                plugin.tagEngine().applyRemoteEquip(playerId, tagId.isEmpty() ? null : tagId);
            } else if ("unequip".equals(action)) {
                plugin.tagEngine().applyRemoteEquip(playerId, null);
            }
        } catch (Exception ignored) {
        }
    }

    private static byte[] encode(String action, UUID playerId, String tagId) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(action);
            out.writeUTF(playerId.toString());
            out.writeUTF(tagId);
            out.flush();
            return bytes.toByteArray();
        } catch (Exception ex) {
            return null;
        }
    }
}
