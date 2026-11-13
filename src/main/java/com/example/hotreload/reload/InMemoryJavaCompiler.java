package com.example.hotreload.reload;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public final class InMemoryJavaCompiler {

    Map<String, byte[]> compile(String className, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new JavaCompileException("未找到 Java 编译器。请使用 JDK 启动应用，不能使用 JRE。");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standardFileManager =
                compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
        try (MemoryFileManager manager = new MemoryFileManager(standardFileManager)) {
            List<String> options = List.of(
                    "--release", Integer.toString(Runtime.version().feature()),
                    "-encoding", "UTF-8",
                    "-parameters",
                    "-proc:none",
                    "-classpath", classpath()
            );
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    manager,
                    diagnostics,
                    options,
                    null,
                    List.of(new SourceFile(className, source))
            );
            if (!Boolean.TRUE.equals(task.call())) {
                throw new JavaCompileException(format(diagnostics));
            }
            return manager.classBytes();
        } catch (JavaCompileException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new JavaCompileException("编译 " + className + " 失败: " + exception.getMessage());
        }
    }

    private static String classpath() {
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        addPathList(entries, System.getProperty("java.class.path"));
        addClassLoader(entries, Thread.currentThread().getContextClassLoader());
        addClassLoader(entries, InMemoryJavaCompiler.class.getClassLoader());
        CodeSource codeSource = InMemoryJavaCompiler.class.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            addUrl(entries, codeSource.getLocation());
        }
        if (entries.isEmpty()) {
            throw new JavaCompileException("无法确定编译 classpath。");
        }
        return String.join(File.pathSeparator, entries);
    }

    private static void addPathList(Set<String> entries, String classPath) {
        if (classPath == null || classPath.isBlank()) {
            return;
        }
        entries.addAll(Arrays.asList(classPath.split(File.pathSeparator)));
    }

    private static void addClassLoader(Set<String> entries, ClassLoader classLoader) {
        ClassLoader current = classLoader;
        while (current != null) {
            if (current instanceof URLClassLoader urlClassLoader) {
                for (URL url : urlClassLoader.getURLs()) {
                    addUrl(entries, url);
                }
            }
            current = current.getParent();
        }
    }

    private static void addUrl(Set<String> entries, URL url) {
        if (url == null || !"file".equals(url.getProtocol())) {
            return;
        }
        try {
            entries.add(Path.of(url.toURI()).toString());
        } catch (Exception ignored) {
            entries.add(url.getPath());
        }
    }

    private static String format(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder builder = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() != Diagnostic.Kind.ERROR) {
                continue;
            }
            JavaFileObject source = diagnostic.getSource();
            if (source != null) {
                builder.append(source.getName()).append(':');
            }
            builder.append(diagnostic.getLineNumber())
                    .append(':')
                    .append(diagnostic.getColumnNumber())
                    .append(' ')
                    .append(diagnostic.getMessage(Locale.ROOT))
                    .append(System.lineSeparator());
        }
        if (builder.isEmpty()) {
            return "编译失败";
        }
        return builder.toString().strip();
    }

    private static final class SourceFile extends SimpleJavaFileObject {

        private final String source;

        private SourceFile(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + ".java"), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static final class ClassFile extends SimpleJavaFileObject {

        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private ClassFile(String className) {
            super(URI.create("bytes:///" + className.replace('.', '/') + ".class"), Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return output;
        }

        private byte[] bytes() {
            return output.toByteArray();
        }
    }

    private static final class MemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

        private final Map<String, ClassFile> files = new LinkedHashMap<>();

        private MemoryFileManager(StandardJavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            ClassFile file = new ClassFile(className);
            files.put(className, file);
            return file;
        }

        private Map<String, byte[]> classBytes() {
            Map<String, byte[]> result = new LinkedHashMap<>();
            files.forEach((name, file) -> result.put(name, file.bytes()));
            return result;
        }
    }

    static final class JavaCompileException extends RuntimeException {

        JavaCompileException(String message) {
            super(message);
        }
    }
}
