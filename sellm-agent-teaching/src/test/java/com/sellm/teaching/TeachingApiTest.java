package com.sellm.teaching;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeachingApiTest {

    @DynamicPropertySource
    static void overrideDb(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
            () -> "jdbc:h2:mem:teaching_api_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StubSmartLayerClient stub;

    @TestConfiguration
    static class Stubs {
        @Bean @Primary
        StubSmartLayerClient stubSmartLayerClient() { return new StubSmartLayerClient(); }
    }

    static class StubSmartLayerClient implements SmartLayerClient {
        final AtomicReference<String> lastIep = new AtomicReference<>();
        volatile boolean throwError = false;
        @Override public String generate(String task, String iepContentOrPlan, String disorderType,
                                         String scene, String mode) {
            if (throwError) throw new SmartLayerException("down");
            lastIep.set(iepContentOrPlan);
            return "[AI 生成] " + task + " 文本";
        }
    }

    private Long createFinalizedPlan(long userId) throws Exception {
        var res = mvc.perform(post("/api/teaching/lesson-plans")
                .header("X-User-Id", String.valueOf(userId))
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "scene", "SCHOOL", "mode", "ONE_ON_ONE", "disorderType", "ASD",
                    "iepContent", "长期目标:共同注意"))))
            .andExpect(status().isOk())
            .andReturn();
        Long id = json.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(post("/api/teaching/lesson-plans/" + id + "/finalize").header("X-User-Id", String.valueOf(userId)))
            .andExpect(status().isOk());
        return id;
    }

    @Test
    void 生成教案草案为DRAFT并调Python() throws Exception {
        stub.throwError = false;
        mvc.perform(post("/api/teaching/lesson-plans")
                .header("X-User-Id", "5")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "scene", "SCHOOL", "mode", "ONE_ON_ONE", "disorderType", "ASD",
                    "iepContent", "长期目标:提升共同注意"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.content").value("[AI 生成] lesson_plan 文本"));
    }

    @Test
    void 教案编辑后定稿为FINALIZED() throws Exception {
        Long id = createFinalizedPlan(5L);
        mvc.perform(get("/api/teaching/lesson-plans/" + id).header("X-User-Id", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FINALIZED"));
    }

    @Test
    void 课件须基于定稿教案否则400() throws Exception {
        // 建草案教案(未定稿)
        var res = mvc.perform(post("/api/teaching/lesson-plans")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of("scene","HOME","mode","GROUP","disorderType","ASD","iepContent","x"))))
            .andReturn();
        Long planId = json.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(post("/api/teaching/courseware")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of("lessonPlanId", planId))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 课件基于定稿教案生成并finalize落存储() throws Exception {
        Long planId = createFinalizedPlan(5L);
        var res = mvc.perform(post("/api/teaching/courseware")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of("lessonPlanId", planId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn();
        Long cwId = json.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(post("/api/teaching/courseware/" + cwId + "/finalize").header("X-User-Id", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FINALIZED"))
            .andExpect(jsonPath("$.data.storageKey").isNotEmpty());
    }

    @Test
    void 他人教案被拒403() throws Exception {
        Long id = createFinalizedPlan(5L);
        mvc.perform(get("/api/teaching/lesson-plans/" + id).header("X-User-Id", "8"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 缺X_User_Id返401() throws Exception {
        mvc.perform(post("/api/teaching/lesson-plans")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("scene","HOME","mode","GROUP","disorderType","ASD","iepContent","x"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void Python不可用时降级DRAFT保留() throws Exception {
        stub.throwError = true;
        mvc.perform(post("/api/teaching/lesson-plans")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of("scene","HOME","mode","GROUP","disorderType","ASD","iepContent","x"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("失败")));
        stub.throwError = false;
    }
}
