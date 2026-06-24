package com.sellm.aids;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P1:文生素材出网脱敏将 subjectNames 纳入屏蔽表(原 bug:空字典,中文姓名明文出网)。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssetSubjectNamesTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturingAnonymizer anon;

    @TestConfiguration
    static class Cfg {
        @Bean("assetTaskExecutor") @Primary
        TaskExecutor syncExecutor() { return new SyncTaskExecutor(); }
        @Bean @Primary CapturingAnonymizer capturingAnonymizer() { return new CapturingAnonymizer(); }
        @Bean @Primary SmartLayerClient stubClient() {
            return (type, prompt) -> GeneratedContent.text("AI 文本");
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

    @Test
    void 提交素材将subjectNames传入脱敏屏蔽表() throws Exception {
        mvc.perform(post("/api/aids/assets").header("X-User-Id", "8")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "type", "IMAGE", "prompt", "为小红设计认识情绪的卡片",
                    "subjectNames", List.of("小红")))))
            .andExpect(status().isAccepted());
        assertTrue(anon.capturedNames.contains("小红"), "儿童姓名应进入脱敏屏蔽表");
    }
}
