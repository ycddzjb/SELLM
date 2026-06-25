package com.sellm.user.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 微信 jscode2session 客户端:用前端 wx.login 的 code 换 openid。
 * 强制 HTTP/1.1(同其他外联适配器);发请求抽成 protected send 便于测试子类化注入假响应、不真连网。
 */
@Component
@EnableConfigurationProperties(WeChatProperties.class)
public class WeChatClient {

    private final WeChatProperties props;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient httpClient;

    public WeChatClient(WeChatProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .build();
    }

    /** 用 code 换 openid;失败抛 BusinessException(INVALID_INPUT)。 */
    public String openidByCode(String code) {
        if (props.getAppId() == null || props.getAppId().isBlank()
                || props.getAppSecret() == null || props.getAppSecret().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "微信登录未配置(缺 AppID/AppSecret)");
        }
        try {
            String url = props.getLoginUrl()
                + "?appid=" + enc(props.getAppId())
                + "&secret=" + enc(props.getAppSecret())
                + "&js_code=" + enc(code)
                + "&grant_type=authorization_code";
            String body = send(url);
            JsonNode node = json.readTree(body);
            // 微信失败时返回 errcode(非 0)+ errmsg;成功返回 openid
            int errcode = node.path("errcode").asInt(0);
            if (errcode != 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "微信登录失败: " + node.path("errmsg").asText("未知错误"));
            }
            String openid = node.path("openid").asText("");
            if (openid.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "微信登录失败: 未返回 openid");
            }
            return openid;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "微信登录调用失败");
        }
    }

    /** 实际 GET 请求;抽成可覆写方法,测试子类化注入假响应,避免真连网。 */
    protected String send(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .GET()
            .build();
        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("微信接口返回非 2xx: " + resp.statusCode());
        }
        return resp.body();
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
