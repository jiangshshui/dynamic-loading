package com.example.hotreload.reload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个可在运行时被替换的 Spring Bean。
 *
 * <p>实现类必须实现业务接口，并且通过该接口注入。容器里保存的是稳定的 JDK 代理，
 * 源码写入完成后会编译出新类、创建新实例，再把代理的目标换成新实例。
 * 已经注入出去的引用不需要重新获取。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HotReload {
}
