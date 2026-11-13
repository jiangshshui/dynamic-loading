package com.example.hotreload.reload;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ClassNameResolver {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    private ClassNameResolver() {
    }

    static String resolve(Path javaFile, String source) {
        String fileName = javaFile.getFileName().toString();
        if (!fileName.endsWith(".java")) {
            throw new IllegalArgumentException("不是 Java 文件: " + javaFile);
        }
        String simpleName = fileName.substring(0, fileName.length() - ".java".length());
        Matcher matcher = PACKAGE_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1) + "." + simpleName;
        }
        return simpleName;
    }
}
