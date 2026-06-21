package com.sellm.teaching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SmartLayerProperties.class)
public class TeachingApplication {
    public static void main(String[] args) {
        SpringApplication.run(TeachingApplication.class, args);
    }
}
