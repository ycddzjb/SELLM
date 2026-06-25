package com.sellm.multimodal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 图像脱敏配置(sellm.image-anon.*)。默认 provider=noop(不改图)。
 * provider=http 时,POST 图片到外部 CV 打码服务,返回打码后图片。
 */
@ConfigurationProperties(prefix = "sellm.image-anon")
public class ImageAnonProperties {
    private String provider = "noop";   // noop | http
    private String endpoint = "";
    private String apiKey = "";
    private int timeoutSeconds = 30;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
