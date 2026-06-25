package com.sellm.teaching;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeachingSanitizeHardBlockTest {

    @DynamicPropertySource
    static void overrideDb(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
            () -> "jdbc:h2:mem:teaching_sanitize_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LocalStub stub;

    @TestConfiguration
    static class Cfg {
        @Bean @Primary
        Anonymizer throwingAnonymizer() {
            return new Anonymizer() {
                public AnonymizationResult anonymize(String text, List<String> names, List<String> schools) {
                    throw new AnonymizationException("forced");
                }
                public String restore(String text, Map<String, String> map) { return text; }
            };
        }
        @Bean @Primary
        LocalStub localStub() { return new LocalStub(); }
    }

    static class LocalStub implements SmartLayerClient {
        volatile boolean called = false;
        @Override public String generate(String task, String c, String d, String s, String m) {
            called = true; return "should-not-be-called";
        }
        @Override public String generateContent(String contentType, String requirement, String optionsJson) {
            called = true; return "should-not-be-called";
        }
    }

    @Test
    void 脱敏失败返400且不调Python() throws Exception {
        mvc.perform(post("/api/teaching/lesson-plans")
                .header("X-User-Id", "5").contentType("application/json")
                .content(json.writeValueAsString(Map.of("scene","HOME","mode","GROUP","disorderType","ASD","iepContent","x"))))
            .andExpect(status().isBadRequest());
        assertFalse(stub.called, "脱敏失败应硬阻断,不调 Python");
    }
}
