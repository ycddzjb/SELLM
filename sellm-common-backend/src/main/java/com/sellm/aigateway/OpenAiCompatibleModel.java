package com.sellm.aigateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OpenAI 兼容大模型适配器:POST {baseUrl}/v1/chat/completions。
 * 兼容 DeepSeek/通义/Moonshot/vLLM 等。仅在 provider=openai 且配置 apiKey 时由 AiModelConfig 装配。
 * 收到的是网关脱敏后的 prompt(占位符),真实姓名/校名不出网。
 */
public class OpenAiCompatibleModel implements AiModel {

    private final AiProperties props;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient httpClient;

    public OpenAiCompatibleModel(AiProperties props) {
        this.props = props;
        // 强制 HTTP/1.1:JDK HttpClient 默认 HTTP/2,与部分网关(如 dashscope)协商时会卡死导致请求超时。
        // connectTimeout 仅管 TCP 连接(固定 10s);整体生成超时由请求级 .timeout() 用 timeoutSeconds 控制。
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public String complete(String anonymizedPrompt) {
        try {
            String body = buildRequestBody(anonymizedPrompt);
            String responseBody = send(body);
            return parseContent(responseBody);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("OpenAI 兼容模型调用失败", e);
        }
    }

    /** 组请求体:{model, messages:[{role:user, content}]}。 */
    String buildRequestBody(String prompt) throws Exception {
        ObjectNode root = json.createObjectNode();
        root.put("model", props.getModel());
        ArrayNode messages = root.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", prompt);
        return json.writeValueAsString(root);
    }

    /** 解析 choices[0].message.content。 */
    String parseContent(String responseBody) throws Exception {
        JsonNode root = json.readTree(responseBody);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new RuntimeException("模型响应缺少 choices[0].message.content");
        }
        return content.asText();
    }

    /** 实际发请求;抽成可覆写方法,测试可子类化注入假响应,避免真连网。 */
    protected String send(String requestBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(props.getBaseUrl() + "/v1/chat/completions"))
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + props.getApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("模型返回非 2xx: " + resp.statusCode());
        }
        return resp.body();
    }
}
