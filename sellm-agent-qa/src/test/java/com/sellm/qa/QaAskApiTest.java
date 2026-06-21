package com.sellm.qa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.qa.dto.QaAnswer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
class QaAskApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StubSmartLayerClient stub;

    @TestConfiguration
    static class Stubs {
        @Bean @Primary
        StubSmartLayerClient stubSmartLayerClient() { return new StubSmartLayerClient(); }
    }

    static class StubSmartLayerClient implements SmartLayerClient {
        final AtomicReference<String> lastQuestion = new AtomicReference<>();
        volatile boolean called = false;
        volatile boolean throwError = false;
        @Override public QaAnswer generate(String anonymizedQuestion, int topK) {
            called = true;
            lastQuestion.set(anonymizedQuestion);
            if (throwError) throw new SmartLayerException("down");
            return new QaAnswer("这是政策解读答案",
                List.of(Map.of("title", "融合教育指南", "source", "kb_policy/doc1")));
        }
    }

    @Test
    void 通用意图调Python并落库会话与消息() throws Exception {
        stub.called = false; stub.throwError = false;
        var res = mvc.perform(post("/api/qa/ask")
                .header("X-User-Id", "7")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("question", "孤独症融合教育政策有哪些"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.answer").value("这是政策解读答案"))
            .andExpect(jsonPath("$.data.routeTo").doesNotExist())
            .andExpect(jsonPath("$.data.sources[0].title").value("融合教育指南"))
            .andReturn();
        assertTrue(stub.called, "GENERAL 意图应调用 Python");
        // conversationId 已生成
        Long convId = json.readTree(res.getResponse().getContentAsString())
            .path("data").path("conversationId").asLong();
        assertTrue(convId > 0);
    }

    @Test
    void 业务意图返深链且不调Python() throws Exception {
        stub.called = false;
        mvc.perform(post("/api/qa/ask")
                .header("X-User-Id", "7")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("question", "帮我用量表评估这个孩子"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.routeTo").value("assessment"))
            .andExpect(jsonPath("$.data.deepLink").value("/assessment"));
        assertFalse(stub.called, "业务意图不应调用 Python");
    }

    @Test
    void 缺X_User_Id返401() throws Exception {
        mvc.perform(post("/api/qa/ask")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("question", "你好"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 他人会话历史被拒403() throws Exception {
        // user 7 建会话
        var ask = mvc.perform(post("/api/qa/ask")
                .header("X-User-Id", "7")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("question", "你好"))))
            .andReturn();
        Long convId = json.readTree(ask.getResponse().getContentAsString())
            .path("data").path("conversationId").asLong();
        // user 8 访问 user 7 的会话
        mvc.perform(get("/api/qa/conversations/" + convId).header("X-User-Id", "8"))
            .andExpect(status().isForbidden());
    }

    @Test
    void Python不可用时优雅降级() throws Exception {
        stub.throwError = true;
        mvc.perform(post("/api/qa/ask")
                .header("X-User-Id", "7")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("question", "什么是特殊教育"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString("暂不可用")));
        stub.throwError = false;
    }

    /**
     * 红线测试1:出网文本经脱敏 —— 含身份证号的问题,到达 Python 层的文本不含原始号码。
     * RegexAnonymizer.ID_CARD = \b\d{17}[\dXx]\b,18位纯数字符合模式。
     */
    @Test
    void 出网文本中身份证号已被脱敏替换() throws Exception {
        stub.called = false; stub.throwError = false;
        String rawId = "110101199003078888";
        mvc.perform(post("/api/qa/ask")
                .header("X-User-Id", "7")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "question", "特殊教育政策对身份证 " + rawId + " 的孩子有何规定"))))
            .andExpect(status().isOk());
        assertTrue(stub.called, "GENERAL 意图应调用 Python");
        String sentText = stub.lastQuestion.get();
        assertNotNull(sentText, "出网文本不应为 null");
        assertFalse(sentText.contains(rawId),
            "出网文本不应包含原始身份证号,实际出网文本: " + sentText);
        assertTrue(sentText.contains("[身份证"),
            "出网文本应含 [身份证N] 占位符,实际出网文本: " + sentText);
    }
}
