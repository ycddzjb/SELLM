package com.sellm.multimodal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按 sellm.image-anon.provider 装配图像脱敏器。
 * provider=http 且 endpoint 非空 → HttpImageAnonymizer;否则 Noop(默认,不改图)。
 */
@Configuration
@EnableConfigurationProperties(ImageAnonProperties.class)
public class ImageAnonConfig {

    @Bean
    public ImageAnonymizer imageAnonymizer(ImageAnonProperties props) {
        if ("http".equalsIgnoreCase(props.getProvider())
                && props.getEndpoint() != null && !props.getEndpoint().isBlank()) {
            return new HttpImageAnonymizer(props);
        }
        return new NoopImageAnonymizer();
    }
}
