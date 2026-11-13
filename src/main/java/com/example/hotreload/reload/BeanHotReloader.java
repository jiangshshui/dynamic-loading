package com.example.hotreload.reload;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

public class BeanHotReloader {

    private static final Logger log = LoggerFactory.getLogger(BeanHotReloader.class);

    private final HotReloadBeanPostProcessor postProcessor;
    private final InMemoryJavaCompiler compiler;
    private final DefaultListableBeanFactory beanFactory;
    private final AtomicInteger destroyCount = new AtomicInteger();

    public BeanHotReloader(
            HotReloadBeanPostProcessor postProcessor,
            InMemoryJavaCompiler compiler,
            DefaultListableBeanFactory beanFactory) {
        this.postProcessor = postProcessor;
        this.compiler = compiler;
        this.beanFactory = beanFactory;
    }

    public void reload(Path javaFile) {
        try {
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            reloadSource(ClassNameResolver.resolve(javaFile, source), source);
        } catch (Exception exception) {
            log.error("读取 {} 失败，保留当前 Bean", javaFile, exception);
        }
    }

    public synchronized void reloadSource(String className, String source) {
        HotReloadBeanPostProcessor.ReloadableBean bean = postProcessor.find(className);
        if (bean == null) {
            log.debug("{} 没有对应的 @HotReload Bean，已忽略", className);
            return;
        }
        Map<String, byte[]> bytecode;
        try {
            bytecode = compiler.compile(className, source);
        } catch (InMemoryJavaCompiler.JavaCompileException exception) {
            log.error("编译失败，保留当前 Bean {}:\n{}", className, exception.getMessage());
            return;
        }
        if (!bytecode.containsKey(className)) {
            log.error("编译结果中没有 {}，保留当前 Bean", className);
            return;
        }

        ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        ReloadClassLoader loader = new ReloadClassLoader(BeanHotReloader.class.getClassLoader(), bytecode);
        Object created = null;
        ReloadSession.open();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            Class<?> newClass = loader.loadClass(className);
            String incompatible = incompatibleReason(newClass, bean.interfaces());
            if (incompatible != null) {
                log.error("重新编译的 {} {}，保留当前 Bean", className, incompatible);
                return;
            }
            created = beanFactory.createBean(newClass);
            Object previous = bean.swap(created);
            created = null;
            destroyQuietly(previous);
            Object current = bean.current();
            log.info(
                    "已替换 Spring Bean [{}]，新实例 {}@{}，累计替换 {} 次",
                    bean.beanName(),
                    current.getClass().getName(),
                    Integer.toHexString(System.identityHashCode(current)),
                    bean.reloadCount()
            );
        } catch (RuntimeException | Error exception) {
            destroyQuietly(created);
            log.error("替换 Bean {} 失败，保留当前实例", className, exception);
        } catch (Exception exception) {
            destroyQuietly(created);
            log.error("加载 {} 失败，保留当前 Bean", className, exception);
        } finally {
            Thread.currentThread().setContextClassLoader(previousLoader);
            ReloadSession.close();
        }
    }

    public int destroyCount() {
        return destroyCount.get();
    }

    public int reloadCount(String className) {
        HotReloadBeanPostProcessor.ReloadableBean bean = postProcessor.find(className);
        return bean == null ? 0 : bean.reloadCount();
    }

    private void destroyQuietly(Object instance) {
        if (instance == null) {
            return;
        }
        try {
            beanFactory.destroyBean(instance);
            destroyCount.incrementAndGet();
        } catch (RuntimeException exception) {
            log.warn("销毁旧实例失败: {}", instance, exception);
        }
    }

    private static String incompatibleReason(Class<?> newClass, Class<?>[] interfaces) {
        if (!Modifier.isPublic(newClass.getModifiers())) {
            return "不是 public 类";
        }
        for (Class<?> interfaceType : interfaces) {
            if (!interfaceType.isAssignableFrom(newClass)) {
                return "没有实现 " + interfaceType.getName();
            }
        }
        return null;
    }
}
