package com.sellm.qa.doc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sellm.doc-analyzer")
public class DocAnalyzerProperties {
    /** mock(默认,不外联) | openai(OpenAI 兼容 vision) */
    private String provider = "mock";
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private int timeoutSeconds = 90;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int t) { this.timeoutSeconds = t; }
}
