package com.example.hotreload.reload;

import java.util.Map;

/**
 * 优先定义本次编译出来的类，避免委派给父加载器时拿到已经加载的旧类。
 */
final class ReloadClassLoader extends ClassLoader {

    private final Map<String, byte[]> classBytes;

    ReloadClassLoader(ClassLoader parent, Map<String, byte[]> classBytes) {
        super(parent);
        this.classBytes = Map.copyOf(classBytes);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                byte[] bytes = classBytes.get(name);
                if (bytes != null) {
                    loaded = defineClass(name, bytes, 0, bytes.length);
                } else {
                    loaded = super.loadClass(name, false);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }
}
