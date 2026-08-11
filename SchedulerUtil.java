package com.dracave.tags.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class SchedulerUtil {
    private SchedulerUtil() {
    }

    public interface Task {
        void cancel();
    }

    public static void runTask(Plugin plugin, Runnable runnable) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
    }

    public static void runTaskEntity(Player player, Plugin plugin, Runnable runnable) {
        player.getScheduler().run(plugin, task -> runnable.run(), null);
    }

    public static void runTaskAsynchronously(Plugin plugin, Runnable runnable) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
    }

    public static void runTaskLater(Plugin plugin, Runnable runnable, long delayTicks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), delayTicks);
    }

    public static Task runTaskTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        var scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> runnable.run(), delayTicks, periodTicks);
        return scheduled::cancel;
    }

    public static Task runTaskTimerAsynchronously(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        long delayMs = delayTicks * 50L;
        long periodMs = periodTicks * 50L;
        var scheduled = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> runnable.run(),
                delayMs, periodMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        return scheduled::cancel;
    }
}
