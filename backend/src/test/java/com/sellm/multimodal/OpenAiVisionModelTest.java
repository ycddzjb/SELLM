package com.sellm.multimodal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.scale.ScaleItem;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class OpenAiVisionModelTest {

    private final ObjectMapper json = new ObjectMapper();

    private MultimodalProperties props() {
        MultimodalProperties p = new MultimodalProperties();
        p.setProvider("openai");
        p.setBaseUrl("https://fake.local");
        p.setApiKey("sk-test");
        p.setModel("qwen-vl-plus");
        return p;
    }

    private final List<ScaleItem> items = List.of(
        new ScaleItem("q1", "社交", "社交", 1, 4),
        new ScaleItem("q2", "沟通", "沟通", 2, 4));

    private final ImageAnonymizer noop = new NoopImageAnonymizer();

    private OpenAiVisionModel model() {
        return new OpenAiVisionModel(props(), noop);
    }

    @Test
    void 请求体含指标指令且带图片时含image_url() throws Exception {
        OpenAiVisionModel m = model();
        String body = m.buildRequestBody(new byte[]{1, 2, 3}, "笔记", items);
        var root = json.readTree(body);
        assertThat(root.path("model").asText()).isEqualTo("qwen-vl-plus");
        var content = root.path("messages").path(0).path("content");
        // 第一段文本含指标 itemId;含 image_url 段
        assertThat(content.path(0).path("text").asText()).contains("q1").contains("q2").contains("JSON");
        boolean hasImage = false;
        for (var c : content) {
            if ("image_url".equals(c.path("type").asText())) {
                assertThat(c.path("image_url").path("url").asText()).startsWith("data:image/jpeg;base64,");
                hasImage = true;
            }
        }
        assertThat(hasImage).isTrue();
    }

    @Test
    void 纯笔记无图片时不含image_url() throws Exception {
        OpenAiVisionModel m = model();
        String body = m.buildRequestBody(null, "只有笔记", items);
        var content = json.readTree(body).path("messages").path(0).path("content");
        for (var c : content) {
            assertThat(c.path("type").asText()).isNotEqualTo("image_url");
        }
    }

    @Test
    void 解析choices内JSON数组为建议() throws Exception {
        OpenAiVisionModel m = model();
        String resp = "{\"choices\":[{\"message\":{\"content\":\"```json\\n[{\\\"itemId\\\":\\\"q1\\\",\\\"score\\\":3,\\\"reason\\\":\\\"good\\\"},{\\\"itemId\\\":\\\"q2\\\",\\\"score\\\":2,\\\"reason\\\":\\\"ok\\\"}]\\n```\"}}]}";
        List<ItemSuggestion> out = m.parseSuggestions(resp, items);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getItemId()).isEqualTo("q1");
        assertThat(out.get(0).getSuggestedScore()).isEqualTo(3.0);
        assertThat(out.get(1).getReason()).isEqualTo("ok");
    }

    @Test
    void 响应无法解析时抛异常() {
        OpenAiVisionModel m = model();
        assertThatThrownBy(() -> m.parseSuggestions(
            "{\"choices\":[{\"message\":{\"content\":\"抱歉无法识别\"}}]}", items))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void analyze走可覆写send不真连网() {
        OpenAiVisionModel m = new OpenAiVisionModel(props(), noop) {
            @Override
            protected String send(String requestBody) {
                return "{\"choices\":[{\"message\":{\"content\":\"[{\\\"itemId\\\":\\\"q1\\\",\\\"score\\\":4,\\\"reason\\\":\\\"r\\\"}]\"}}]}";
            }
        };
        List<ItemSuggestion> out = m.analyze(new byte[]{1}, null, items);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getSuggestedScore()).isEqualTo(4.0);
    }

    @Test
    void analyze出网前用脱敏后的图像字节() {
        // 标记式脱敏器:把任何图片替换成固定标记字节
        byte[] marker = "MASKED".getBytes();
        ImageAnonymizer marking = image -> marker;
        StringBuilder capturedBody = new StringBuilder();
        OpenAiVisionModel m = new OpenAiVisionModel(props(), marking) {
            @Override
            protected String send(String requestBody) {
                capturedBody.append(requestBody);
                return "{\"choices\":[{\"message\":{\"content\":\"[{\\\"itemId\\\":\\\"q1\\\",\\\"score\\\":1,\\\"reason\\\":\\\"r\\\"}]\"}}]}";
            }
        };
        m.analyze(new byte[]{1, 2, 3}, null, items);
        // 出网请求体里的图片应是脱敏后 marker 的 base64,而非原图
        String expectedB64 = java.util.Base64.getEncoder().encodeToString(marker);
        assertThat(capturedBody.toString()).contains(expectedB64);
        String originalB64 = java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        assertThat(capturedBody.toString()).doesNotContain(originalB64);
    }
}

