package com.sellm.multimodal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 外部 CV 打码服务图像脱敏(provider=http 时)。
 * POST 原图字节 → 服务返回打码后图片字节。
 * Fail-safe:任何失败都抛异常阻断,绝不让未脱敏原图继续出网(沿用文本 Anonymizer 的硬阻断红线)。
 */
public class HttpImageAnonymizer implements ImageAnonymizer {

    private final ImageAnonProperties props;
    private final HttpClient httpClient;

    public HttpImageAnonymizer(ImageAnonProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public byte[] sanitize(byte[] image) {
        if (image == null || image.length == 0) {
            return image;
        }
        try {
            byte[] result = send(image);
            if (result == null || result.length == 0) {
                throw new RuntimeException("脱敏服务返回空结果");
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("图像脱敏失败,已阻断出网", e);
        }
    }

    /** 实际发请求;protected 便于测试子类化注入假响应,不真连网。 */
    protected byte[] send(byte[] image) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(props.getEndpoint()))
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(image));
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            b.header("Authorization", "Bearer " + props.getApiKey());
        }
        HttpResponse<byte[]> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("脱敏服务返回非 2xx: " + resp.statusCode());
        }
        return resp.body();
    }
}
