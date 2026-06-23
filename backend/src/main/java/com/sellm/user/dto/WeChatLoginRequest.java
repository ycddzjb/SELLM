package com.sellm.user.dto;

/** 微信小程序登录请求:前端 wx.login 取得的临时 code。 */
public class WeChatLoginRequest {
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
