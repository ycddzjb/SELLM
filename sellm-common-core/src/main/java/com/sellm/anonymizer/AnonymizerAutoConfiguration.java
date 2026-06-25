package com.sellm.anonymizer;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** 自动装配默认脱敏器,任何依赖 sellm-common-core 的模块无需组件扫描即可获得 Anonymizer。 */
@AutoConfiguration
public class AnonymizerAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(Anonymizer.class)
    public Anonymizer regexAnonymizer() {
        return new RegexAnonymizer();
    }
}
