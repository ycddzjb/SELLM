package com.sellm.multimodal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音识别配置(sellm.speech.*)。默认 provider=mock,不外联。
 * ⚠️ 真实 ASR 会把含儿童语音的音频发往第三方;启用即代表已获监护人知情同意并自担合规风险。
 */
@ConfigurationProperties(prefix = "sellm.speech")
public class SpeechProperties {
    private String provider = "mock";   // mock | openai
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "whisper-1";
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
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
