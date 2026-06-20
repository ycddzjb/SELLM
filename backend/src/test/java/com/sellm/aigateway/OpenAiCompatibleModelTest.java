package com.sellm.aigateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OpenAiCompatibleModelTest {

    private final ObjectMapper json = new ObjectMapper();

    private AiProperties props() {
        AiProperties p = new AiProperties();
        p.setProvider("openai");
        p.setBaseUrl("https://fake.local");
        p.setApiKey("sk-test");
        p.setModel("test-model");
        return p;
    }

    @Test
    void 请求体含model与user消息() throws Exception {
        OpenAiCompatibleModel m = new OpenAiCompatibleModel(props());
        String body = m.buildRequestBody("已脱敏的[儿童1]文本");
        var root = json.readTree(body);
        assertThat(root.path("model").asText()).isEqualTo("test-model");
        assertThat(root.path("messages").path(0).path("role").asText()).isEqualTo("user");
        assertThat(root.path("messages").path(0).path("content").asText()).contains("[儿童1]");
    }

    @Test
    void 解析choices内容() throws Exception {
        OpenAiCompatibleModel m = new OpenAiCompatibleModel(props());
        String resp = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"生成的报告\"}}]}";
        assertThat(m.parseContent(resp)).isEqualTo("生成的报告");
    }

    @Test
    void 响应缺content抛异常() {
        OpenAiCompatibleModel m = new OpenAiCompatibleModel(props());
        assertThatThrownBy(() -> m.parseContent("{\"choices\":[]}"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void complete走可覆写send不真连网() {
        // 子类化覆写 send,注入假响应,验证 complete 全链路(不触发真实 HTTP)
        OpenAiCompatibleModel m = new OpenAiCompatibleModel(props()) {
            @Override
            protected String send(String requestBody) {
                return "{\"choices\":[{\"message\":{\"content\":\"假模型输出\"}}]}";
            }
        };
        assertThat(m.complete("[儿童1] 在 [学校1] 的表现")).isEqualTo("假模型输出");
    }

    @Test
    void send非2xx时complete抛运行时异常() {
        OpenAiCompatibleModel m = new OpenAiCompatibleModel(props()) {
            @Override
            protected String send(String requestBody) {
                throw new RuntimeException("模型返回非 2xx: 500");
            }
        };
        assertThatThrownBy(() -> m.complete("x")).isInstanceOf(RuntimeException.class);
    }
}
