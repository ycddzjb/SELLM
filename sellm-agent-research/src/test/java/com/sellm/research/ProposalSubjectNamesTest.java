package com.sellm.research;

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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P1:课题书生成出网脱敏将 subjectNames 纳入屏蔽表(原 bug:空字典)。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProposalSubjectNamesTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturingAnonymizer anon;

    @TestConfiguration
    static class Cfg {
        @Bean @Primary CapturingAnonymizer capturingAnonymizer() { return new CapturingAnonymizer(); }
        @Bean @Primary SmartLayerClient stubClient() { return topic -> "AI 课题书"; }
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
    void 课题书生成将subjectNames传入脱敏屏蔽表() throws Exception {
        mvc.perform(post("/api/research/proposals").header("X-User-Id", "9")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "topic", "针对小李的个案干预研究",
                    "subjectNames", List.of("小李")))))
            .andExpect(status().isOk());
        assertTrue(anon.capturedNames.contains("小李"), "姓名应进入脱敏屏蔽表");
    }
}
