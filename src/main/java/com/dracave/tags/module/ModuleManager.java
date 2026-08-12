package com.dracave.tags.module;

import com.dracave.tags.DraCaveTags;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 模块管理器。按注册顺序启用模块，按逆序禁用模块，
 * 确保依赖关系正确的模块先启用、后关闭。
 */
public final class ModuleManager {

    private final DraCaveTags plugin;
    private final List<Module> modules = new ArrayList<>();
    private boolean enabled;

    public ModuleManager(@NotNull DraCaveTags plugin) {
        this.plugin = plugin;
    }

    public void register(@NotNull Module module) {
        modules.add(module);
    }

    public void enableAll() {
        for (Module module : modules) {
            try {
                plugin.getLogger().info("启用模块: " + module.identifier());
                module.enable();
            } catch (Exception ex) {
                plugin.getLogger().severe("模块 " + module.identifier() + " 启用失败: " + ex.getMessage());
                ex.printStackTrace();
                break;
            }
        }
        enabled = true;
    }

    public void disableAll() {
        if (!enabled) {
            return;
        }
        List<Module> reversed = new ArrayList<>(modules);
        Collections.reverse(reversed);
        for (Module module : reversed) {
            try {
                module.disable();
            } catch (Exception ex) {
                plugin.getLogger().warning("模块 " + module.identifier() + " 禁用异常: " + ex.getMessage());
            }
        }
        enabled = false;
    }

    public void reloadAll() {
        for (Module module : modules) {
            try {
                module.reload();
            } catch (Exception ex) {
                plugin.getLogger().warning("模块 " + module.identifier() + " 重载异常: " + ex.getMessage());
            }
        }
    }

    @NotNull
    public List<Module> modules() {
        return Collections.unmodifiableList(modules);
    }
}
