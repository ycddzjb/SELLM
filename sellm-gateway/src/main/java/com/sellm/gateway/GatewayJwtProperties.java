package com.sellm.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "sellm.gateway.jwt")
public class GatewayJwtProperties {
    /** JWT 签名密钥(与各服务同一密钥)。 */
    private String secret = "";
    /** 免鉴权白名单路径前缀。 */
    private List<String> whitelist = List.of("/api/auth/", "/actuator/health");

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public List<String> getWhitelist() { return whitelist; }
    public void setWhitelist(List<String> whitelist) { this.whitelist = whitelist; }
}
