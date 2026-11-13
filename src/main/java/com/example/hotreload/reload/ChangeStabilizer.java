package com.example.hotreload.reload;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用文件修改时间和大小判断一次编辑是否已经写完。
 * 指纹还在变化时只记录“正在修改”；连续 {@code quietNanos} 不变后才允许替换 Bean。
 */
final class ChangeStabilizer {

    private final Map<Path, Fingerprint> stable = new HashMap<>();
    private final Map<Path, Pending> pending = new HashMap<>();

    void baseline(Map<Path, Fingerprint> files) {
        stable.clear();
        stable.putAll(files);
        pending.clear();
    }

    Result observe(Map<Path, Fingerprint> files, long nowNanos, long quietNanos) {
        Set<Path> live = files.keySet();
        stable.keySet().removeIf(path -> !live.contains(path));
        pending.keySet().removeIf(path -> !live.contains(path));

        List<Path> startedWaiting = new ArrayList<>();
        List<Path> ready = new ArrayList<>();
        for (Map.Entry<Path, Fingerprint> entry : files.entrySet()) {
            Path path = entry.getKey();
            Fingerprint fingerprint = entry.getValue();
            if (fingerprint.equals(stable.get(path))) {
                pending.remove(path);
                continue;
            }
            Pending existing = pending.get(path);
            if (existing == null || !existing.fingerprint.equals(fingerprint)) {
                pending.put(path, new Pending(fingerprint, nowNanos));
                startedWaiting.add(path);
                continue;
            }
            if (nowNanos - existing.since >= quietNanos) {
                stable.put(path, fingerprint);
                pending.remove(path);
                ready.add(path);
            }
        }
        return new Result(List.copyOf(startedWaiting), List.copyOf(ready));
    }

    record Fingerprint(long modifiedMillis, long size) {
    }

    record Result(List<Path> startedWaiting, List<Path> ready) {
    }

    private record Pending(Fingerprint fingerprint, long since) {
    }
}
