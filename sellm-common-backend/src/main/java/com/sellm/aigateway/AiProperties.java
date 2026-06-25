package com.sellm.aigateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI 模型配置(sellm.ai.*)。默认 provider=mock,不外联;配置 openai+apiKey 才启用真实模型。 */
@ConfigurationProperties(prefix = "sellm.ai")
public class AiProperties {
    private String provider = "mock";   // mock | openai
    private String baseUrl = "";        // 如 https://api.openai.com 或兼容服务地址
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private int timeoutSeconds = 60;    // 整体请求超时;大模型生成长报告常需 20-40s

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
