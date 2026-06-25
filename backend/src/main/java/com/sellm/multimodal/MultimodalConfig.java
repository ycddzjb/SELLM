package com.sellm.multimodal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按 provider 装配多模态模型(vision)与语音识别(ASR)。
 * vision:sellm.multimodal.provider=openai 且 apiKey 非空 → 真实;否则 Mock(默认,不外联)。
 * ASR:sellm.speech.provider=openai 且 apiKey 非空 → 真实;否则 Mock(默认,不外联)。
 */
@Configuration
@EnableConfigurationProperties({MultimodalProperties.class, SpeechProperties.class})
public class MultimodalConfig {

    @Bean
    public MultimodalModel multimodalModel(MultimodalProperties props, ImageAnonymizer imageAnonymizer) {
        if ("openai".equalsIgnoreCase(props.getProvider())
                && props.getApiKey() != null && !props.getApiKey().isBlank()) {
            return new OpenAiVisionModel(props, imageAnonymizer);
        }
        return new MockMultimodalModel();
    }

    @Bean
    public SpeechModel speechModel(SpeechProperties props) {
        if ("openai".equalsIgnoreCase(props.getProvider())
                && props.getApiKey() != null && !props.getApiKey().isBlank()) {
            return new OpenAiSpeechModel(props);
        }
        return new MockSpeechModel();
    }
}
