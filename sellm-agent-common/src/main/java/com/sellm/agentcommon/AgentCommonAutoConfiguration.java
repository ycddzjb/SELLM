package com.sellm.agentcommon;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 自动装配 Agent 脚手架:异常处理器 + 智能层属性。依赖 sellm-agent-common 的模块零样板获得。 */
@AutoConfiguration
@EnableConfigurationProperties(SmartLayerProperties.class)
public class AgentCommonAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AgentExceptionHandler.class)
    public AgentExceptionHandler agentExceptionHandler() {
        return new AgentExceptionHandler();
    }
}
