package com.sellm.qa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.qa.dto.QaAnswer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 匿名问答 + 多模态文档分析(豆包式公开体验)回归。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QaAnonApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @TestConfiguration
    static class Cfg {
        @Bean @Primary SmartLayerClient stubClient() {
            return (q, topK) -> new QaAnswer("匿名也能得到的答案", List.of());
        }
    }

    @Test
    void 匿名提问无需登录返回答案() throws Exception {
        // 不带 X-User-Id(匿名),走通用意图问题
        mvc.perform(post("/api/qa/ask").contentType("application/json")
                .content(json.writeValueAsString(Map.of("question", "孤独症融合教育政策有哪些"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data.answer").value("匿名也能得到的答案"))
            .andExpect(jsonPath("$.data.conversationId").doesNotExist());  // 匿名不落库会话
    }

    @Test
    void 匿名上传文本文档分析返回结果() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "note.txt", "text/plain", "融合教育要点摘要".getBytes());
        mvc.perform(multipart("/api/qa/analyze").file(file).param("question", "这讲了什么"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").isNotEmpty());  // mock 分析器返回占位分析文本
    }
}
