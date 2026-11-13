package com.example.hotreload.service;

import org.springframework.stereotype.Component;

@Component
public class NameDecorator {

    public String decorate(String name) {
        return name == null ? "" : name.trim();
    }
}
