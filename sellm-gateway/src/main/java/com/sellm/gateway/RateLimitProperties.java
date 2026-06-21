package com.sellm.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sellm.gateway.rate-limit")
public class RateLimitProperties {
    /** 是否启用限流。 */
    private boolean enabled = true;
    /** 窗口内允许的最大请求数。 */
    private int limit = 100;
    /** 窗口秒数(固定窗口)。 */
    private int windowSeconds = 60;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
    public int getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
}
