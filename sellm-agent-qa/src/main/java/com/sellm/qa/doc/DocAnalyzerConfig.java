package com.sellm.qa.doc;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 文档分析器装配:provider=openai 且配 key → 真实;否则默认 Mock(不外联)。 */
@Configuration
@EnableConfigurationProperties(DocAnalyzerProperties.class)
public class DocAnalyzerConfig {

    @Bean
    public DocAnalyzer docAnalyzer(DocAnalyzerProperties props) {
        if ("openai".equalsIgnoreCase(props.getProvider())
            && props.getApiKey() != null && !props.getApiKey().isBlank()) {
            return new OpenAiDocAnalyzer(props);
        }
        return new MockDocAnalyzer();
    }
}
