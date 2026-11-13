package com.example.hotreload.reload;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HotReloadProperties.class)
@ConditionalOnProperty(prefix = "hot-reload", name = "enabled", matchIfMissing = true)
public class HotReloadConfiguration {

    @Bean
    static HotReloadBeanPostProcessor hotReloadBeanPostProcessor() {
        return new HotReloadBeanPostProcessor();
    }

    @Bean
    InMemoryJavaCompiler inMemoryJavaCompiler() {
        return new InMemoryJavaCompiler();
    }

    @Bean
    BeanHotReloader beanHotReloader(
            HotReloadBeanPostProcessor postProcessor,
            InMemoryJavaCompiler compiler,
            DefaultListableBeanFactory beanFactory) {
        return new BeanHotReloader(postProcessor, compiler, beanFactory);
    }

    @Bean
    JavaSourceWatcher javaSourceWatcher(
            HotReloadProperties properties,
            BeanHotReloader reloader,
            HotReloadBeanPostProcessor postProcessor) {
        return new JavaSourceWatcher(properties, reloader, postProcessor);
    }
}
