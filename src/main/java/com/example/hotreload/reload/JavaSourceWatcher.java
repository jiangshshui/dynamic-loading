package com.example.hotreload.reload;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.beans.factory.DisposableBean;

public class JavaSourceWatcher implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(JavaSourceWatcher.class);
    private static final Set<String> SKIP_DIRECTORIES = Set.of("target", "build", ".git", ".idea");

    private final HotReloadProperties properties;
    private final BeanHotReloader reloader;
    private final HotReloadBeanPostProcessor postProcessor;
    private final ChangeStabilizer stabilizer = new ChangeStabilizer();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "hot-reload-watcher");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean started = new AtomicBoolean();
    private ScheduledFuture<?> future;

    public JavaSourceWatcher(
            HotReloadProperties properties,
            BeanHotReloader reloader,
            HotReloadBeanPostProcessor postProcessor) {
        this.properties = properties;
        this.reloader = reloader;
        this.postProcessor = postProcessor;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        postProcessor.bindDestructionCallbacks();
        Path root = Path.of(properties.getSourceRoot()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            log.warn("热替换未启动，源码目录不存在: {}。请从项目根目录启动，或修改 hot-reload.source-root", root);
            return;
        }
        long quietNanos = properties.getQuietPeriod().toNanos();
        long pollMillis = Math.max(50L, properties.getPollInterval().toMillis());
        try {
            stabilizer.baseline(scan(root));
        } catch (IOException exception) {
            log.error("初始化源码快照失败: {}", root, exception);
            return;
        }
        log.info(
                "热替换已启动，正在监听 {}（每 {} ms 轮询，文件连续 {} ms 无变化后替换 Bean）",
                root,
                pollMillis,
                properties.getQuietPeriod().toMillis()
        );
        if (postProcessor.beans().isEmpty()) {
            log.warn("没有发现 @HotReload Bean。请在实现类上添加注解，并通过它的业务接口注入");
        } else {
            for (HotReloadBeanPostProcessor.ReloadableBean bean : postProcessor.beans()) {
                log.info("可热替换的 Bean: {} -> {}", bean.beanName(), bean.className());
            }
        }
        log.info("试一下: curl 'http://localhost:8080/greet?name=tao' ，然后修改 GreetingServiceImpl 并保存");
        future = executor.scheduleWithFixedDelay(() -> poll(root, quietNanos), pollMillis, pollMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        if (future != null) {
            future.cancel(true);
        }
        executor.shutdownNow();
    }

    private void poll(Path root, long quietNanos) {
        try {
            if (!Files.isDirectory(root)) {
                return;
            }
            Map<Path, ChangeStabilizer.Fingerprint> files = scan(root);
            ChangeStabilizer.Result result = stabilizer.observe(files, System.nanoTime(), quietNanos);
            for (Path path : result.startedWaiting()) {
                log.info("检测到 {} 正在修改，等待写入结束", path.getFileName());
            }
            for (Path path : result.ready()) {
                ChangeStabilizer.Fingerprint expected = files.get(path);
                ChangeStabilizer.Fingerprint latest = fingerprint(path);
                if (latest == null || !latest.equals(expected)) {
                    log.info("{} 在替换前又发生了变化，继续等待", path.getFileName());
                    continue;
                }
                log.info("{} 已写入完成，开始重新加载", path.getFileName());
                reloader.reload(path);
            }
        } catch (Exception exception) {
            log.error("扫描源码目录失败: {}", root, exception);
        }
    }

    private static Map<Path, ChangeStabilizer.Fingerprint> scan(Path root) throws IOException {
        Map<Path, ChangeStabilizer.Fingerprint> files = new LinkedHashMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                if (!dir.equals(root)) {
                    String name = dir.getFileName().toString();
                    if (name.startsWith(".") || SKIP_DIRECTORIES.contains(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && file.getFileName().toString().endsWith(".java")) {
                    files.put(
                            file.toAbsolutePath().normalize(),
                            new ChangeStabilizer.Fingerprint(attributes.lastModifiedTime().toMillis(), attributes.size())
                    );
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private static ChangeStabilizer.Fingerprint fingerprint(Path file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return new ChangeStabilizer.Fingerprint(attributes.lastModifiedTime().toMillis(), attributes.size());
        } catch (IOException exception) {
            return null;
        }
    }
}
