package com.sellm.aigateway;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按 sellm.ai.provider 装配 AiModel。
 * provider=openai 且 apiKey 非空 → 真实模型;否则 Mock(默认,不外联)。
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiModelConfig {

    @Bean
    public AiModel aiModel(AiProperties props) {
        if ("openai".equalsIgnoreCase(props.getProvider())
                && props.getApiKey() != null && !props.getApiKey().isBlank()) {
            return new OpenAiCompatibleModel(props);
        }
        return new MockAiModel();
    }
}
