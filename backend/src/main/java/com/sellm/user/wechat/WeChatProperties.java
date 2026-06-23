package com.sellm.user.wechat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信小程序登录配置(sellm.wechat.*)。
 * appId/appSecret 走环境变量;未配置时 WeChatLoginService 返回明确错误(不静默放行)。
 */
@ConfigurationProperties(prefix = "sellm.wechat")
public class WeChatProperties {
    /** 小程序 AppID。 */
    private String appId = "";
    /** 小程序 AppSecret(敏感,走环境变量)。 */
    private String appSecret = "";
    /** jscode2session 接口地址(可覆盖便于私有代理/测试)。 */
    private String loginUrl = "https://api.weixin.qq.com/sns/jscode2session";
    /** 新建微信家长用户默认归属机构(可空,待管理端审核分配)。 */
    private Long defaultOrgId;
    private int timeoutSeconds = 10;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
    public String getLoginUrl() { return loginUrl; }
    public void setLoginUrl(String loginUrl) { this.loginUrl = loginUrl; }
    public Long getDefaultOrgId() { return defaultOrgId; }
    public void setDefaultOrgId(Long defaultOrgId) { this.defaultOrgId = defaultOrgId; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
