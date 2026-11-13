package com.example.hotreload.reload;

/**
 * {@code GET /hot-reload} 返回的当前替换状态。
 */
public record HotReloadView(
        String beanName,
        String className,
        int reloadCount,
        String targetClass,
        int targetIdentityHash,
        String classLoader
) {
}
