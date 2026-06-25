package com.sellm.multimodal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按 sellm.multimodal.provider 装配多模态模型。
 * provider=openai 且 apiKey 非空 → 真实 vision;否则 Mock(默认,不外联)。
 */
@Configuration
@EnableConfigurationProperties(MultimodalProperties.class)
public class MultimodalConfig {

    @Bean
    public MultimodalModel multimodalModel(MultimodalProperties props, ImageAnonymizer imageAnonymizer) {
        if ("openai".equalsIgnoreCase(props.getProvider())
                && props.getApiKey() != null && !props.getApiKey().isBlank()) {
            return new OpenAiVisionModel(props, imageAnonymizer);
        }
        return new MockMultimodalModel();
    }
}
