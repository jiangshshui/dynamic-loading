package com.example.hotreload.reload;

/**
 * 替换过程中创建新实例时，避免后处理器再次把新实例包成代理。
 */
final class ReloadSession {

    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private ReloadSession() {
    }

    static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    static void open() {
        ACTIVE.set(Boolean.TRUE);
    }

    static void close() {
        ACTIVE.remove();
    }
}
