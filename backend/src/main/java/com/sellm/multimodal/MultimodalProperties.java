package com.sellm.multimodal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多模态模型配置(sellm.multimodal.*)。默认 provider=mock,不外联。
 * ⚠️ 真实 vision 会把含儿童面部的图片发往第三方,无法占位符脱敏 ——
 * 启用即代表已获监护人知情同意并自担合规风险。
 */
@ConfigurationProperties(prefix = "sellm.multimodal")
public class MultimodalProperties {
    private String provider = "mock";   // mock | openai
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "qwen-vl-plus";
    private int timeoutSeconds = 90;    // vision 生成通常更慢

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
