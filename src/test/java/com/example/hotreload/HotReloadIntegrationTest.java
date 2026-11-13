package com.example.hotreload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.hotreload.reload.BeanHotReloader;
import com.example.hotreload.service.GreetingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HotReloadIntegrationTest {

    private static final String CLASS_NAME = "com.example.hotreload.service.GreetingServiceImpl";

    @Autowired
    private GreetingService greetingService;

    @Autowired
    private BeanHotReloader reloader;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void replacesTheExistingBeanAndKeepsTheInjectedReference() throws Exception {
        reloader.reloadSource(CLASS_NAME, GreetingSources.greeting("\"Hello, \" + nameDecorator.decorate(name)"));
        assertThat(greetingService.greet(" tao ")).isEqualTo("Hello, tao");
        mockMvc.perform(get("/greet").param("name", "tao"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, tao"));

        int reloads = reloader.reloadCount(CLASS_NAME);
        int destroyed = reloader.destroyCount();
        GreetingService injected = greetingService;
        reloader.reloadSource(CLASS_NAME, GreetingSources.greeting("\"Hi, \" + nameDecorator.decorate(name)"));

        assertThat(greetingService).isSameAs(injected);
        assertThat(greetingService.greet(" tao ")).isEqualTo("Hi, tao");
        mockMvc.perform(get("/greet").param("name", "tao"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hi, tao"));
        assertThat(reloader.reloadCount(CLASS_NAME)).isEqualTo(reloads + 1);
        assertThat(reloader.destroyCount()).isEqualTo(destroyed + 1);

        String statusBody = mockMvc.perform(get("/hot-reload"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(statusBody)
                .contains("\"reloadCount\":" + reloader.reloadCount(CLASS_NAME))
                .contains(CLASS_NAME);
    }

    @Test
    void keepsTheCurrentBeanWhenCompilationFails() {
        reloader.reloadSource(CLASS_NAME, GreetingSources.greeting("\"Hi, \" + nameDecorator.decorate(name)"));
        int reloads = reloader.reloadCount(CLASS_NAME);
        int destroyed = reloader.destroyCount();

        reloader.reloadSource(CLASS_NAME, GreetingSources.greeting("notDefined"));

        assertThat(greetingService.greet("tao")).isEqualTo("Hi, tao");
        assertThat(reloader.reloadCount(CLASS_NAME)).isEqualTo(reloads);
        assertThat(reloader.destroyCount()).isEqualTo(destroyed);
    }

    @Test
    void keepsTheCurrentBeanWhenTheNewClassDropsTheInterface() {
        reloader.reloadSource(CLASS_NAME, GreetingSources.greeting("\"Hi, \" + nameDecorator.decorate(name)"));
        int reloads = reloader.reloadCount(CLASS_NAME);

        reloader.reloadSource(CLASS_NAME, """
                package com.example.hotreload.service;

                import org.springframework.stereotype.Service;

                @Service
                public class GreetingServiceImpl {
                    public String greet(String name) {
                        return "gone";
                    }
                }
                """);

        assertThat(greetingService.greet("tao")).isEqualTo("Hi, tao");
        assertThat(reloader.reloadCount(CLASS_NAME)).isEqualTo(reloads);
    }
}
