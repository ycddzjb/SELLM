package com.sellm.qa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.anonymizer.AnonymizationException;
import com.sellm.anonymizer.AnonymizationResult;
import com.sellm.anonymizer.Anonymizer;
import com.sellm.qa.dto.QaAnswer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 红线测试2:脱敏失败时硬阻断 —— AnonymizationException 应导致 HTTP 400 且不调 Python。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QaSanitizeHardBlockTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LocalStub localStub;

    @TestConfiguration
    static class HardBlockStubs {

        /** 捕获 Python 是否被调用 */
        @Bean @Primary
        LocalStub localStub() { return new LocalStub(); }

        /** Anonymizer 总是抛 AnonymizationException,模拟脱敏校验失败 */
        @Bean @Primary
        Anonymizer throwingAnonymizer() {
            return new Anonymizer() {
                @Override
                public AnonymizationResult anonymize(String text, List<String> names, List<String> schools) {
                    throw new AnonymizationException("forced: 脱敏校验失败");
                }
                @Override
                public String restore(String text, Map<String, String> map) { return text; }
            };
        }

        /** SmartLayerClient 代理,记录是否被调用 */
        @Bean @Primary
        SmartLayerClient blockingSmartLayerClient(LocalStub stub) {
            return (anonymizedQuestion, topK) -> {
                stub.called.set(true);
                return new QaAnswer("不应到达", List.of());
            };
        }
    }

    /** 辅助 stub:仅追踪是否被调用 */
    static class LocalStub {
        final AtomicBoolean called = new AtomicBoolean(false);
    }

    @Test
    void 脱敏失败时返400且不调Python() throws Exception {
        localStub.called.set(false);
        mvc.perform(post("/api/qa/ask")
                .header("X-User-Id", "7")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("question", "特殊教育政策介绍"))))
            .andExpect(status().isBadRequest());
        assertFalse(localStub.called.get(), "脱敏失败后不应调用 Python(SmartLayerClient)");
    }
}
