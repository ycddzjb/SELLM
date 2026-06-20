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

    @Test
    void 请求体含指标指令且带图片时含image_url() throws Exception {
        OpenAiVisionModel m = new OpenAiVisionModel(props());
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
        OpenAiVisionModel m = new OpenAiVisionModel(props());
        String body = m.buildRequestBody(null, "只有笔记", items);
        var content = json.readTree(body).path("messages").path(0).path("content");
        for (var c : content) {
            assertThat(c.path("type").asText()).isNotEqualTo("image_url");
        }
    }

    @Test
    void 解析choices内JSON数组为建议() throws Exception {
        OpenAiVisionModel m = new OpenAiVisionModel(props());
        String resp = "{\"choices\":[{\"message\":{\"content\":\"```json\\n[{\\\"itemId\\\":\\\"q1\\\",\\\"score\\\":3,\\\"reason\\\":\\\"good\\\"},{\\\"itemId\\\":\\\"q2\\\",\\\"score\\\":2,\\\"reason\\\":\\\"ok\\\"}]\\n```\"}}]}";
        List<ItemSuggestion> out = m.parseSuggestions(resp, items);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getItemId()).isEqualTo("q1");
        assertThat(out.get(0).getSuggestedScore()).isEqualTo(3.0);
        assertThat(out.get(1).getReason()).isEqualTo("ok");
    }

    @Test
    void 响应无法解析时抛异常() {
        OpenAiVisionModel m = new OpenAiVisionModel(props());
        assertThatThrownBy(() -> m.parseSuggestions(
            "{\"choices\":[{\"message\":{\"content\":\"抱歉无法识别\"}}]}", items))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void analyze走可覆写send不真连网() {
        OpenAiVisionModel m = new OpenAiVisionModel(props()) {
            @Override
            protected String send(String requestBody) {
                return "{\"choices\":[{\"message\":{\"content\":\"[{\\\"itemId\\\":\\\"q1\\\",\\\"score\\\":4,\\\"reason\\\":\\\"r\\\"}]\"}}]}";
            }
        };
        List<ItemSuggestion> out = m.analyze(new byte[]{1}, null, items);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getSuggestedScore()).isEqualTo(4.0);
    }
}
