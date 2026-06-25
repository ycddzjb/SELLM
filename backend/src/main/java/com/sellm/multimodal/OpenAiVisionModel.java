package com.sellm.multimodal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sellm.scale.ScaleItem;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * OpenAI 兼容 vision 多模态模型(provider=openai 时启用)。
 * 仅在显式配置 apiKey 时由 MultimodalConfig 装配。
 * ⚠️ 出网会把图片(含儿童面部)发往第三方,无法脱敏 —— 合规风险由配置方承担。
 */
public class OpenAiVisionModel implements MultimodalModel {

    private final MultimodalProperties props;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient httpClient;
    private final ImageAnonymizer imageAnonymizer;

    public OpenAiVisionModel(MultimodalProperties props, ImageAnonymizer imageAnonymizer) {
        this.props = props;
        this.imageAnonymizer = imageAnonymizer;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)   // 同 OpenAiCompatibleModel:避 HTTP/2 协商卡死
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public List<ItemSuggestion> analyze(byte[] media, String noteText, List<ScaleItem> items) {
        try {
            // 出网前对图像脱敏(默认 Noop 不改图;配 http 则外部打码,失败硬阻断)
            byte[] safeMedia = imageAnonymizer.sanitize(media);
            String body = buildRequestBody(safeMedia, noteText, items);
            String resp = send(body);
            return parseSuggestions(resp, items);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("多模态模型调用失败", e);
        }
    }

    @Override
    public String describe(byte[] media, String noteText) {
        try {
            byte[] safeMedia = imageAnonymizer.sanitize(media);
            ObjectNode root = json.createObjectNode();
            root.put("model", props.getModel());
            ArrayNode messages = root.putArray("messages");
            ObjectNode msg = messages.addObject();
            msg.put("role", "user");
            ArrayNode content = msg.putArray("content");
            StringBuilder instr = new StringBuilder();
            instr.append("你是特殊教育康复评估专家。请客观描述画面中儿童的训练表现(动作、专注、互动、情绪等),");
            instr.append("用于后续能力诊断。只描述观察到的事实,不下结论。\n");
            if (noteText != null && !noteText.isBlank()) {
                instr.append("教师笔记:").append(noteText).append("\n");
            }
            content.addObject().put("type", "text").put("text", instr.toString());
            if (safeMedia != null && safeMedia.length > 0) {
                String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(safeMedia);
                content.addObject().put("type", "image_url").putObject("image_url").put("url", dataUrl);
            }
            String resp = send(json.writeValueAsString(root));
            JsonNode r = json.readTree(resp);
            String text = r.path("choices").path(0).path("message").path("content").asText("");
            return text.isBlank() ? "[影像描述为空]" : text;
        } catch (Exception e) {
            return "[影像描述失败] 请教师据画面补充文字描述。";
        }
    }

    /** 组 vision 请求体:文本指令(列出 items,要求返回 JSON 数组)+ 可选图片(base64 data URL)。 */
    String buildRequestBody(byte[] media, String noteText, List<ScaleItem> items) throws Exception {
        StringBuilder instr = new StringBuilder();
        instr.append("你是特殊教育评估专家。请据提供的素材(图片/训练笔记)对以下量表指标逐项给出 0 到各自满分的评分建议,");
        instr.append("并简述理由。严格返回 JSON 数组,每项形如 {\"itemId\":\"..\",\"score\":数字,\"reason\":\"..\"},不要额外文字。\n");
        instr.append("指标:\n");
        for (ScaleItem it : items) {
            instr.append("- itemId=").append(it.getItemId())
                .append(" 维度=").append(it.getDimension())
                .append(" 题干=").append(it.getStem())
                .append(" 满分=").append(it.getMaxScore()).append("\n");
        }
        if (noteText != null && !noteText.isBlank()) {
            instr.append("训练笔记:").append(noteText).append("\n");
        }

        ObjectNode root = json.createObjectNode();
        root.put("model", props.getModel());
        ArrayNode messages = root.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        ArrayNode content = msg.putArray("content");
        content.addObject().put("type", "text").put("text", instr.toString());
        if (media != null && media.length > 0) {
            String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(media);
            ObjectNode img = content.addObject();
            img.put("type", "image_url");
            img.putObject("image_url").put("url", dataUrl);
        }
        return json.writeValueAsString(root);
    }

    /** 解析模型返回的 JSON 数组 → ItemSuggestion;容错:解析失败时回退每 item 中位分。 */
    List<ItemSuggestion> parseSuggestions(String responseBody, List<ScaleItem> items) throws Exception {
        JsonNode root = json.readTree(responseBody);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        // 模型可能用 ```json 包裹,抽取首个 [ ... ]
        int s = content.indexOf('['), e = content.lastIndexOf(']');
        List<ItemSuggestion> out = new ArrayList<>();
        if (s >= 0 && e > s) {
            JsonNode arr = json.readTree(content.substring(s, e + 1));
            for (JsonNode n : arr) {
                out.add(new ItemSuggestion(
                    n.path("itemId").asText(),
                    n.path("score").asDouble(0),
                    n.path("reason").asText("")));
            }
        }
        if (out.isEmpty()) {
            throw new RuntimeException("多模态模型响应无法解析为指标建议");
        }
        return out;
    }

    /** 实际发请求;protected 便于测试子类化注入假响应,不真连网。 */
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
            throw new RuntimeException("多模态模型返回非 2xx: " + resp.statusCode());
        }
        return resp.body();
    }
}
