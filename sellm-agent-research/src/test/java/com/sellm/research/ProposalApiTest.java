package com.sellm.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProposalApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StubClient stub;

    @TestConfiguration
    static class Cfg {
        @Bean @Primary StubClient stubClient() { return new StubClient(); }
    }
    static class StubClient implements SmartLayerClient {
        final AtomicReference<String> lastTopic = new AtomicReference<>();
        volatile boolean throwError = false;
        @Override public String generate(String topic) {
            if (throwError) throw new com.sellm.agentcommon.SmartLayerException("down");
            lastTopic.set(topic);
            return "[AI 生成] 课题申报书";
        }
    }

    private long createDraft(long uid) throws Exception {
        var res = mvc.perform(post("/api/research/proposals")
                .header("X-User-Id", String.valueOf(uid)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("topic", "融合教育师资研究"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    @Test
    void 生成课题书草案DRAFT() throws Exception {
        stub.throwError = false;
        createDraft(7L);
    }

    @Test
    void 编辑后定稿FINALIZED且冻结() throws Exception {
        long id = createDraft(7L);
        mvc.perform(put("/api/research/proposals/" + id).header("X-User-Id","7")
                .contentType("application/json").content(json.writeValueAsString(Map.of("content","改稿"))))
            .andExpect(status().isOk());
        mvc.perform(post("/api/research/proposals/" + id + "/finalize").header("X-User-Id","7"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FINALIZED"));
        // 已定稿编辑→400
        mvc.perform(put("/api/research/proposals/" + id).header("X-User-Id","7")
                .contentType("application/json").content(json.writeValueAsString(Map.of("content","再改"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 他人课题书403() throws Exception {
        long id = createDraft(7L);
        mvc.perform(get("/api/research/proposals/" + id).header("X-User-Id","8"))
            .andExpect(status().isForbidden());
    }

    @Test
    void Python不可用降级DRAFT保留() throws Exception {
        stub.throwError = true;
        mvc.perform(post("/api/research/proposals").header("X-User-Id","7")
                .contentType("application/json").content(json.writeValueAsString(Map.of("topic","x"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("失败")));
        stub.throwError = false;
    }
}
