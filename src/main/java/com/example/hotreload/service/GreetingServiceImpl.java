package com.example.hotreload.service;

import com.example.hotreload.reload.HotReload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

/**
 * 修改这个类并保存。文件大小和修改时间稳定约 800ms 后，容器会编译新类并替换本 Bean。
 * 其他组件继续持有 {@link GreetingService}，下一次调用进入新实例。
 */
@HotReload
@Service
public class GreetingServiceImpl implements GreetingService, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(GreetingServiceImpl.class);

    private final NameDecorator nameDecorator;

    public GreetingServiceImpl(NameDecorator nameDecorator) {
        this.nameDecorator = nameDecorator;
    }

    @Override
    public String greet(String name) {
        return "Hello, " + nameDecorator.decorate(name);
    }

    @Override
    public void destroy() {
        log.info("销毁 GreetingServiceImpl 实例 {}", Integer.toHexString(System.identityHashCode(this)));
    }
}
