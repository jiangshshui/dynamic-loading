package com.example.hotreload.reload;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

/**
 * 把 {@link HotReload} Bean 换成 JDK 代理，并在容器关闭时销毁当前实例。
 */
public class HotReloadBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware, Ordered {

    private final List<ReloadableBean> beans = new CopyOnWriteArrayList<>();
    private DefaultListableBeanFactory beanFactory;

    @Override
    public void setBeanFactory(org.springframework.beans.factory.BeanFactory beanFactory) {
        if (beanFactory instanceof DefaultListableBeanFactory listableBeanFactory) {
            this.beanFactory = listableBeanFactory;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (ReloadSession.isActive() || bean == null) {
            return bean;
        }
        Class<?> userClass = ClassUtils.getUserClass(bean);
        if (!isReloadable(userClass) || beans.stream().anyMatch(existing -> existing.beanName().equals(beanName))) {
            return bean;
        }
        Class<?>[] interfaces = businessInterfaces(userClass);
        if (interfaces.length == 0) {
            throw new IllegalStateException("@" + HotReload.class.getSimpleName()
                    + " Bean [" + beanName + "] 必须实现 public 业务接口，并通过接口注入。类: " + userClass.getName());
        }
        TargetHandler handler = new TargetHandler(bean);
        Object proxy = Proxy.newProxyInstance(interfaces[0].getClassLoader(), interfaces, handler);
        beans.add(new ReloadableBean(beanName, userClass.getName(), interfaces, handler));
        return proxy;
    }

    /**
     * Spring 会在初始化结束后登记原始实例的销毁回调。容器就绪后再覆盖一次，
     * 关闭时销毁代理当前指向的实例，而不是启动时的那个实例。
     */
    void bindDestructionCallbacks() {
        if (beanFactory == null) {
            return;
        }
        for (ReloadableBean bean : beans) {
            TargetHandler handler = bean.handler();
            beanFactory.registerDisposableBean(bean.beanName(), () -> {
                Object current = handler.current();
                if (current != null) {
                    beanFactory.destroyBean(current);
                }
            });
        }
    }

    ReloadableBean find(String className) {
        return beans.stream()
                .filter(bean -> bean.className().equals(className))
                .findFirst()
                .orElse(null);
    }

    List<ReloadableBean> beans() {
        return List.copyOf(beans);
    }

    public List<HotReloadView> snapshot() {
        List<HotReloadView> views = new ArrayList<>();
        for (ReloadableBean bean : beans) {
            Object target = bean.handler().current();
            Class<?> targetClass = target.getClass();
            ClassLoader classLoader = targetClass.getClassLoader();
            views.add(new HotReloadView(
                    bean.beanName(),
                    bean.className(),
                    bean.reloadCount(),
                    targetClass.getName(),
                    System.identityHashCode(target),
                    classLoader == null ? "bootstrap" : classLoader.getClass().getSimpleName() + "@"
                            + Integer.toHexString(System.identityHashCode(classLoader))
            ));
        }
        return views;
    }

    private static boolean isReloadable(Class<?> userClass) {
        if (AnnotatedElementUtils.hasAnnotation(userClass, HotReload.class)) {
            return true;
        }
        for (Class<?> interfaceType : ClassUtils.getAllInterfacesForClass(userClass)) {
            if (AnnotatedElementUtils.hasAnnotation(interfaceType, HotReload.class)) {
                return true;
            }
        }
        return false;
    }

    private static Class<?>[] businessInterfaces(Class<?> userClass) {
        List<Class<?>> interfaces = new ArrayList<>();
        for (Class<?> interfaceType : ClassUtils.getAllInterfacesForClass(userClass)) {
            String name = interfaceType.getName();
            if (!Modifier.isPublic(interfaceType.getModifiers())
                    || name.startsWith("org.springframework.")
                    || name.startsWith("java.")
                    || name.startsWith("javax.")
                    || name.startsWith("jakarta.")) {
                continue;
            }
            interfaces.add(interfaceType);
        }
        return interfaces.toArray(Class<?>[]::new);
    }

    record ReloadableBean(String beanName, String className, Class<?>[] interfaces, TargetHandler handler) {

        int reloadCount() {
            return handler.reloadCount.get();
        }

        Object swap(Object next) {
            return handler.swap(next);
        }

        Object current() {
            return handler.current();
        }
    }

    static final class TargetHandler implements InvocationHandler {

        private volatile Object delegate;
        private final AtomicInteger reloadCount = new AtomicInteger();

        private TargetHandler(Object delegate) {
            this.delegate = delegate;
        }

        private Object current() {
            return delegate;
        }

        private synchronized Object swap(Object next) {
            Object previous = delegate;
            delegate = next;
            reloadCount.incrementAndGet();
            return previous;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "HotReloadProxy[" + delegate.getClass().getName() + "@"
                            + Integer.toHexString(System.identityHashCode(delegate)) + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.invoke(delegate, args);
                };
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }
}
