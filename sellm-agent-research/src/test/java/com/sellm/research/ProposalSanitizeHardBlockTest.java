package com.sellm.research;

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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProposalSanitizeHardBlockTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LocalStub stub;

    @TestConfiguration
    static class Cfg {
        @Bean @Primary Anonymizer throwingAnonymizer() {
            return new Anonymizer() {
                public AnonymizationResult anonymize(String t, List<String> n, List<String> s) {
                    throw new AnonymizationException("forced");
                }
                public String restore(String t, Map<String,String> m) { return t; }
            };
        }
        @Bean @Primary LocalStub localStub() { return new LocalStub(); }
    }
    static class LocalStub implements SmartLayerClient {
        volatile boolean called = false;
        @Override public String generate(String topic) { called = true; return "x"; }
    }

    @Test
    void 脱敏失败返400且不调Python() throws Exception {
        mvc.perform(post("/api/research/proposals").header("X-User-Id","7")
                .contentType("application/json").content(json.writeValueAsString(Map.of("topic","x"))))
            .andExpect(status().isBadRequest());
        assertFalse(stub.called, "脱敏失败应硬阻断");
    }
}
