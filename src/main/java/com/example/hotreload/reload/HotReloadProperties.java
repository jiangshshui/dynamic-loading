package com.example.hotreload.reload;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hot-reload")
public class HotReloadProperties {

    /**
     * 关闭后不再代理 Bean，也不监听源码。
     */
    private boolean enabled = true;

    /**
     * Java 源码根目录，布局与 {@code src/main/java} 相同。
     */
    private String sourceRoot = "src/main/java";

    /**
     * 扫描源码目录的间隔。
     */
    private Duration pollInterval = Duration.ofMillis(300);

    /**
     * 文件指纹连续保持不变的时间。达到后才认为修改已经完成。
     */
    private Duration quietPeriod = Duration.ofMillis(800);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSourceRoot() {
        return sourceRoot;
    }

    public void setSourceRoot(String sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public Duration getQuietPeriod() {
        return quietPeriod;
    }

    public void setQuietPeriod(Duration quietPeriod) {
        this.quietPeriod = quietPeriod;
    }
}
