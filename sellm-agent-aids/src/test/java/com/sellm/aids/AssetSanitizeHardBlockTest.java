package com.sellm.aids;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 脱敏硬阻断:@Async 任务内脱敏失败 → 任务 FAILED + 绝不调 Python(stub.called == false)。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssetSanitizeHardBlockTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LocalStub stub;

    @TestConfiguration
    static class Cfg {
        @Bean("assetTaskExecutor") @Primary
        TaskExecutor syncExecutor() { return new SyncTaskExecutor(); }
        @Bean @Primary Anonymizer throwingAnonymizer() {
            return new Anonymizer() {
                public AnonymizationResult anonymize(String t, List<String> n, List<String> s) {
                    throw new AnonymizationException("forced");
                }
                public String restore(String t, Map<String, String> m) { return t; }
            };
        }
        @Bean @Primary LocalStub localStub() { return new LocalStub(); }
    }
    static class LocalStub implements SmartLayerClient {
        volatile boolean called = false;
        @Override public GeneratedContent generate(String type, String prompt) { called = true; return GeneratedContent.text("x"); }
    }

    @Test
    void 脱敏失败任务FAILED且不调Python() throws Exception {
        // POST 阶段只受理(202),失败体现在任务状态
        var res = mvc.perform(post("/api/aids/assets").header("X-User-Id", "7")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("type", "IMAGE", "prompt", "为张三设计卡片"))))
            .andExpect(status().isAccepted())
            .andReturn();
        long taskId = json.readTree(res.getResponse().getContentAsString())
            .path("data").path("taskId").asLong();

        mvc.perform(get("/api/aids/tasks/" + taskId).header("X-User-Id", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.error").value(org.hamcrest.Matchers.containsString("脱敏")));
        assertFalse(stub.called, "脱敏失败应硬阻断,绝不调 Python");
    }
}
