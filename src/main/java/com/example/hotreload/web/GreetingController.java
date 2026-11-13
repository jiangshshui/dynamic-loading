package com.example.hotreload.web;

import com.example.hotreload.service.GreetingService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

    private final GreetingService greetingService;

    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping(value = "/greet", produces = MediaType.TEXT_PLAIN_VALUE)
    public String greet(@RequestParam(defaultValue = "world") String name) {
        return greetingService.greet(name);
    }
}
