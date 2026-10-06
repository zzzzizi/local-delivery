package com.localdelivery.config;

import com.localdelivery.service.DemoUserService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoDataConfiguration {
    @Bean
    @ConditionalOnProperty(name = "app.seed-demo-users", havingValue = "true")
    ApplicationRunner seedDemoUsers(DemoUserService users) {
        return args -> users.seed();
    }
}
