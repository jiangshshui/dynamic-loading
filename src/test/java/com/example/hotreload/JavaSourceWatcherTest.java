package com.example.hotreload;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.hotreload.reload.BeanHotReloader;
import com.example.hotreload.service.GreetingService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class JavaSourceWatcherTest {

    private static final String CLASS_NAME = "com.example.hotreload.service.GreetingServiceImpl";
    private static Path sourceRoot;

    @DynamicPropertySource
    static void sourceRoot(DynamicPropertyRegistry registry) throws IOException {
        sourceRoot = Files.createTempDirectory("hot-reload-src");
        registry.add("hot-reload.source-root", () -> sourceRoot.toString());
        registry.add("hot-reload.poll-interval", () -> "100ms");
        registry.add("hot-reload.quiet-period", () -> "400ms");
    }

    @AfterAll
    static void deleteSourceRoot() throws IOException {
        if (sourceRoot == null || Files.notExists(sourceRoot)) {
            return;
        }
        try (var paths = Files.walk(sourceRoot)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    @Autowired
    private GreetingService greetingService;

    @Autowired
    private BeanHotReloader reloader;

    @Test
    void replacesBeanAfterTheJavaFileFinishesWriting() throws Exception {
        assertThat(greetingService.greet("tao")).isEqualTo("Hello, tao");

        writeAtomically(GreetingSources.greeting("\"A-\" + nameDecorator.decorate(name)"));
        writeAtomically(GreetingSources.greeting("\"B-\" + nameDecorator.decorate(name)"));

        awaitMessage("B-tao");
        assertThat(reloader.reloadCount(CLASS_NAME)).isEqualTo(1);

        Thread.sleep(700);
        assertThat(greetingService.greet("tao")).isEqualTo("B-tao");
        assertThat(reloader.reloadCount(CLASS_NAME)).isEqualTo(1);
    }

    private static void writeAtomically(String source) throws IOException {
        Path target = sourceRoot.resolve("GreetingServiceImpl.java");
        Path temporary = sourceRoot.resolve("GreetingServiceImpl.java.tmp");
        Files.writeString(temporary, source);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void awaitMessage(String expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        String latest = "";
        while (System.nanoTime() < deadline) {
            latest = greetingService.greet("tao");
            if (expected.equals(latest)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("等待热替换超时，当前结果: " + latest);
    }
}
