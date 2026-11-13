package com.example.hotreload;

final class GreetingSources {

    private GreetingSources() {
    }

    static String greeting(String expression) {
        return """
                package com.example.hotreload.service;

                import com.example.hotreload.reload.HotReload;
                import org.springframework.stereotype.Service;

                @HotReload
                @Service
                public class GreetingServiceImpl implements GreetingService {
                    private final NameDecorator nameDecorator;

                    public GreetingServiceImpl(NameDecorator nameDecorator) {
                        this.nameDecorator = nameDecorator;
                    }

                    @Override
                    public String greet(String name) {
                        return %s;
                    }
                }
                """.formatted(expression);
    }
}
