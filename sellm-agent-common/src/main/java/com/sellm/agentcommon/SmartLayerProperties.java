package com.sellm.agentcommon;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 智能层连接配置。所有 agent 共用一个 Python 智能层地址。 */
@ConfigurationProperties(prefix = "sellm.smart-layer")
public class SmartLayerProperties {
    private String baseUrl = "http://localhost:8090";
    private int timeoutSeconds = 30;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
