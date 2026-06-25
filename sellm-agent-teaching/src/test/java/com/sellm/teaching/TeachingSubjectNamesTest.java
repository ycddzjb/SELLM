package com.sellm.teaching;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-2 回归:验证调用方传入的 subjectNames 确实进入脱敏屏蔽表(names 参数),
 * 使中文姓名等命名 PII 被脱敏,而非空字典(原 bug:List.of() 导致姓名明文出网)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeachingSubjectNamesTest {

    @DynamicPropertySource
    static void overrideDb(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
            () -> "jdbc:h2:mem:teaching_subjectnames_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturingAnonymizer anon;

    @TestConfiguration
    static class Cfg {
        @Bean @Primary CapturingAnonymizer capturingAnonymizer() { return new CapturingAnonymizer(); }
        @Bean @Primary SmartLayerClient stubClient() {
            return new SmartLayerClient() {
                @Override public String generate(String task, String c, String d, String s, String m) { return "AI 草案"; }
                @Override public String generateContent(String contentType, String requirement, String optionsJson) { return "AI 草案"; }
            };
        }
    }

    /** 捕获 anonymize 收到的 names,供断言。脱敏行为模拟为原样返回。 */
    static class CapturingAnonymizer implements Anonymizer {
        final CopyOnWriteArrayList<String> capturedNames = new CopyOnWriteArrayList<>();
        @Override public AnonymizationResult anonymize(String text, List<String> names, List<String> schools) {
            capturedNames.addAll(names);
            return new AnonymizationResult(text, Map.of());
        }
        @Override public String restore(String text, Map<String, String> map) { return text; }
    }

    @Test
    void 教案生成将subjectNames传入脱敏屏蔽表() throws Exception {
        mvc.perform(post("/api/teaching/lesson-plans")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "scene", "HOME", "mode", "GROUP", "disorderType", "ASD",
                    "iepContent", "小明在社交沟通方面需支持",
                    "subjectNames", List.of("小明", "阳光小学")))))
            .andExpect(status().isOk());
        assertTrue(anon.capturedNames.contains("小明"), "儿童姓名应进入脱敏屏蔽表");
        assertTrue(anon.capturedNames.contains("阳光小学"), "校名应进入脱敏屏蔽表");
    }

    @Test
    void 未传subjectNames时屏蔽表为空不报错() throws Exception {
        mvc.perform(post("/api/teaching/lesson-plans")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "scene", "HOME", "mode", "GROUP", "disorderType", "ASD",
                    "iepContent", "通用 IEP 内容"))))
            .andExpect(status().isOk());
        // 未传时 safeNames 返回空,不抛 NPE
    }
}
