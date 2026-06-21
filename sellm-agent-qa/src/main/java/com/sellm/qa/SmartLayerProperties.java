package com.sellm.qa;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sellm.qa.smart-layer")
public class SmartLayerProperties {
    /** Python 智能层基地址。 */
    private String baseUrl = "http://localhost:8090";
    /** 请求超时秒数。 */
    private int timeoutSeconds = 30;
    /** RAG 检索 topK。 */
    private int topK = 5;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
}
