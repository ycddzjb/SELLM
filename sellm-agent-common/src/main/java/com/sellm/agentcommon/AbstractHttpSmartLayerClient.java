package com.sellm.agentcommon;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 智能层 HTTP 客户端抽象基类:封装传输(HTTP/1.1 + send + 2xx 检查)。子类拼请求体/解析响应。 */
public abstract class AbstractHttpSmartLayerClient {

    protected final SmartLayerProperties props;
    private final HttpClient httpClient;

    protected AbstractHttpSmartLayerClient(SmartLayerProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .build();
    }

    /**
     * POST jsonBody 到 props.baseUrl + path,返回响应体。
     * 强制 HTTP/1.1(JDK 默认 HTTP/2 与部分网关协商卡死)。非 2xx / 异常包 SmartLayerException。
     */
    protected String send(String path, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + path))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new SmartLayerException("智能层返回非 2xx: " + resp.statusCode());
            }
            return resp.body();
        } catch (SmartLayerException e) {
            throw e;
        } catch (Exception e) {
            throw new SmartLayerException("智能层调用失败", e);
        }
    }
}
