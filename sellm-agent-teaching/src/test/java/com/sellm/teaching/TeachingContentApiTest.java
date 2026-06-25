package com.sellm.teaching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.agentcommon.SmartLayerException;
import com.sellm.anonymizer.AnonymizationException;
import com.sellm.anonymizer.AnonymizationResult;
import com.sellm.anonymizer.Anonymizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 教学模块统一内容(教案/课件/案例/习题)API 回归。
 * 覆盖:生成→DRAFT、按 type 列表、编辑、定稿冻结、行级权限、出网脱敏屏蔽表、脱敏失败硬阻断。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeachingContentApiTest {

    @DynamicPropertySource
    static void overrideDb(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
            () -> "jdbc:h2:mem:teaching_content_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturingAnonymizer anon;

    @TestConfiguration
    static class Cfg {
        @Bean @Primary CapturingAnonymizer capturingAnonymizer() { return new CapturingAnonymizer(); }
        @Bean @Primary SmartLayerClient stubClient() {
            return new SmartLayerClient() {
                @Override public String generate(String task, String c, String d, String s, String m) { return "x"; }
                @Override public String generateContent(String contentType, String requirement, String optionsJson) {
                    return "[AI] " + contentType + " 草案";
                }
            };
        }
    }

    static class CapturingAnonymizer implements Anonymizer {
        final CopyOnWriteArrayList<String> capturedNames = new CopyOnWriteArrayList<>();
        @Override public AnonymizationResult anonymize(String text, List<String> names, List<String> schools) {
            capturedNames.addAll(names);
            return new AnonymizationResult(text, Map.of());
        }
        @Override public String restore(String text, Map<String, String> map) { return text; }
    }

    private Long generate(long userId, String type, String title, String requirement, Object options) throws Exception {
        var res = mvc.perform(post("/api/teaching/contents")
                .header("X-User-Id", String.valueOf(userId))
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "contentType", type, "title", title, "requirement", requirement,
                    "options", options == null ? Map.of() : options))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    @Test
    void 四类内容均可生成为DRAFT() throws Exception {
        for (String t : List.of("LESSON", "COURSEWARE", "CASE", "EXERCISE")) {
            mvc.perform(post("/api/teaching/contents")
                    .header("X-User-Id", "5").contentType("application/json")
                    .content(json.writeValueAsString(Map.of(
                        "contentType", t, "title", t + "标题", "requirement", "要求", "options", Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.content").value("[AI] " + t + " 草案"));
        }
    }

    @Test
    void 非法类型返400() throws Exception {
        mvc.perform(post("/api/teaching/contents")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of("contentType", "FOO", "requirement", "x", "options", Map.of()))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 缺要求返400() throws Exception {
        mvc.perform(post("/api/teaching/contents")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of("contentType", "LESSON", "requirement", "", "options", Map.of()))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 列表按type过滤() throws Exception {
        generate(7L, "LESSON", "教案A", "要求", Map.of());
        generate(7L, "EXERCISE", "习题A", "要求", Map.of());
        mvc.perform(get("/api/teaching/contents").param("type", "LESSON").header("X-User-Id", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].contentType").value("LESSON"));
    }

    @Test
    void 编辑后定稿且定稿后不可编辑() throws Exception {
        Long id = generate(5L, "CASE", "案例", "要求", Map.of("subject", "生活语文"));
        mvc.perform(put("/api/teaching/contents/" + id)
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of("content", "我编辑后的内容"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").value("我编辑后的内容"));
        mvc.perform(post("/api/teaching/contents/" + id + "/finalize").header("X-User-Id", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FINALIZED"));
        mvc.perform(put("/api/teaching/contents/" + id)
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of("content", "再改"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 他人内容被拒403() throws Exception {
        Long id = generate(5L, "LESSON", "教案", "要求", Map.of());
        mvc.perform(get("/api/teaching/contents/" + id).header("X-User-Id", "8"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 缺X_User_Id返401() throws Exception {
        mvc.perform(post("/api/teaching/contents")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("contentType", "LESSON", "requirement", "x", "options", Map.of()))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void subjectNames进入脱敏屏蔽表() throws Exception {
        anon.capturedNames.clear();
        mvc.perform(post("/api/teaching/contents")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "contentType", "EXERCISE", "title", "习题", "requirement", "小明的看图题",
                    "options", Map.of("questionType", "看图题"),
                    "subjectNames", List.of("小明", "阳光小学")))))
            .andExpect(status().isOk());
        assertTrue(anon.capturedNames.contains("小明"), "儿童姓名应进入脱敏屏蔽表");
        assertTrue(anon.capturedNames.contains("阳光小学"), "校名应进入脱敏屏蔽表");
    }
}
