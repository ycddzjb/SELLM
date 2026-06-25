package com.sellm.qa.doc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * OpenAI 兼容 vision 文档分析(provider=openai + 配 key 时启用)。
 * ⚠️ 出网会把(已脱敏的)提示与文件发往第三方 —— 合规由配置方承担。
 * 发请求抽为 protected send(),便于测试子类注入假响应,不真连网;强制 HTTP/1.1。
 */
public class OpenAiDocAnalyzer implements DocAnalyzer {

    private final DocAnalyzerProperties props;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient httpClient;

    public OpenAiDocAnalyzer(DocAnalyzerProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public String analyze(String filename, String mimeType, byte[] bytes, String anonymizedPrompt) {
        try {
            String body = buildRequestBody(filename, mimeType, bytes, anonymizedPrompt);
            String resp = send(body);
            JsonNode root = json.readTree(resp);
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("文档分析调用失败", e);
        }
    }

    String buildRequestBody(String filename, String mimeType, byte[] bytes, String prompt) throws Exception {
        ObjectNode root = json.createObjectNode();
        root.put("model", props.getModel());
        ArrayNode messages = root.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        ArrayNode content = msg.putArray("content");
        String instr = "你是特殊教育领域助手。请分析以下文档/图片并给出要点。"
            + (prompt != null && !prompt.isBlank() ? ("用户问题:" + prompt) : "");
        content.addObject().put("type", "text").put("text", instr);
        if (mimeType != null && mimeType.startsWith("image/") && bytes != null) {
            String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
            ObjectNode img = content.addObject();
            img.put("type", "image_url");
            img.putObject("image_url").put("url", dataUrl);
        } else if (bytes != null) {
            // 文本类:直接拼入(已脱敏)
            String text = new String(bytes, StandardCharsets.UTF_8);
            content.addObject().put("type", "text")
                .put("text", "文档内容:\n" + (text.length() > 8000 ? text.substring(0, 8000) : text));
        }
        return json.writeValueAsString(root);
    }

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
            throw new RuntimeException("文档分析返回非 2xx: " + resp.statusCode());
        }
        return resp.body();
    }
}
