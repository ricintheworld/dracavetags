package com.dracave.tags.module;

/**
 * 功能模块契约。所有功能以模块形式装配，生命周期由
 * {@link ModuleManager} 统一编排，模块之间通过共享上下文解耦。
 * <p>
 * enable/disable 两阶段，外加可选的 reload 热更新。
 */
public interface Module {

    /**
     * 启用模块：创建并绑定该模块负责的服务到启动上下文。
     * 抛出异常将阻止后续模块加载并记录严重错误。
     *
     * @throws Exception 模块初始化失败
     */
    void enable() throws Exception;

    /**
     * 卸载模块：释放资源、注销监听、取消任务、关闭执行器。
     * 实现应保证幂等，即便启用阶段失败也允许被调用。
     */
    void disable();

    /**
     * 重载模块持有的可热更新配置。默认不参与热重载。
     */
    default void reload() {
    }

    /**
     * 模块标识，仅用于日志可读性。
     */
    default String identifier() {
        return getClass().getSimpleName();
    }
}
