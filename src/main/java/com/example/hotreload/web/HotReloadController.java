package com.example.hotreload.web;

import com.example.hotreload.reload.HotReloadBeanPostProcessor;
import com.example.hotreload.reload.HotReloadView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HotReloadController {

    private final HotReloadBeanPostProcessor postProcessor;

    public HotReloadController(HotReloadBeanPostProcessor postProcessor) {
        this.postProcessor = postProcessor;
    }

    @GetMapping("/hot-reload")
    public List<HotReloadView> list() {
        return postProcessor.snapshot();
    }
}
