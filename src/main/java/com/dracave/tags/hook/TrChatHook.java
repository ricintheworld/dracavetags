package com.dracave.tags.hook;

import com.dracave.tags.DraCaveTags;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Applies DraCaveTags' selected color directly to TrChat's raw message component. */
public final class TrChatHook {
    private static final String EVENT_CLASS = "me.arasple.mc.trchat.api.event.TrChatEvent";

    private TrChatHook() {
    }

    public static boolean register(DraCaveTags plugin) {
        if (!plugin.getConfig().getBoolean("chat-color.trchat-native", true)) {
            return false;
        }
        Plugin trChat = Bukkit.getPluginManager().getPlugin("TrChat");
        if (trChat == null || !trChat.isEnabled()) {
            return false;
        }
        try {
            ClassLoader loader = trChat.getClass().getClassLoader();
            Class<?> rawEventClass = Class.forName(EVENT_CLASS, true, loader);
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                plugin.getLogger().warning("TrChatEvent 不是 Bukkit Event，无法启用原生聊天颜色适配");
                return false;
            }

            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Method getPlayer = rawEventClass.getMethod("getPlayer");
            Method getComponent = rawEventClass.getMethod("getComponent");
            Class<?> componentType = getComponent.getReturnType();
            Method color = findColorMethod(componentType);
            Method setComponent = findMethod(rawEventClass, "setComponent", componentType);
            AtomicBoolean loggedFailure = new AtomicBoolean();
            Listener listener = new Listener() { };
            EventExecutor executor = (ignored, event) -> {
                if (!eventClass.isInstance(event) || plugin.chatColorEngine() == null) {
                    return;
                }
                try {
                    Object sender = getPlayer.invoke(event);
                    if (!(sender instanceof Player player)) {
                        return;
                    }
                    String hex = plugin.chatColorEngine().colorHex(player.getUniqueId());
                    if (hex.isEmpty()) {
                        return;
                    }
                    Object component = getComponent.invoke(event);
                    if (component != null) {
                        Object colored = color.invoke(component, Color.decode(hex));
                        if (setComponent != null && colored != null) {
                            setComponent.invoke(event, colored);
                        }
                    }
                } catch (ReflectiveOperationException | IllegalArgumentException ex) {
                    if (loggedFailure.compareAndSet(false, true)) {
                        plugin.getLogger().warning("TrChat 聊天颜色应用失败: " + ex.getMessage());
                    }
                }
            };

            Bukkit.getPluginManager().registerEvent(
                    eventClass, listener, EventPriority.HIGHEST, executor, plugin, true);
            plugin.getLogger().info("已启用 TrChat 2.x 原生消息颜色适配");
            return true;
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("当前 TrChat 版本没有 TrChatEvent，继续使用 PlaceholderAPI 变量模式");
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("TrChat 原生适配初始化失败: " + ex.getMessage());
        }
        return false;
    }

    private static Method findColorMethod(Class<?> componentType) {
        for (String name : new String[]{"color", "K"}) {
            try {
                return componentType.getMethod(name, Color.class);
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Method method : componentType.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 1 && parameters[0] == Color.class) {
                return method;
            }
        }
        throw new IllegalStateException("TrChat ComponentText has no color method");
    }

    private static Method findMethod(Class<?> owner, String name, Class<?> parameter) {
        try {
            return owner.getMethod(name, parameter);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}