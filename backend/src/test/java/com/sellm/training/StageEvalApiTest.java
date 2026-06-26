package com.sellm.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.PromptRequest;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 阶段评估 + 纵向对比(集成):两周期跑通 delta 量化对比 + AI 叙述。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StageEvalApiTest {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",
            () -> "jdbc:h2:mem:stage_eval_api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepository;

    @TestConfiguration
    static class StubAi {
        @Bean @Primary
        AiGateway stubGateway() {
            return (PromptRequest req) -> "[AI叙述] 能力提升表现/未达标训练/方案适配性建议(保留有效,优化低效)。";
        }
    }

    private String token;

    private long setupCycleWithScore(long childId, String beadScore) throws Exception {
        String resp = mvc.perform(post("/api/training-cycles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId", childId, "title", "C"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long cycleId = json.readTree(resp).path("data").path("id").asLong();
        mvc.perform(multipart("/api/training-cycles/" + cycleId + "/records")
                .param("mediaType", "TEXT").param("noteText", "bead practice")
                .param("scores", "[{\"item\":\"bead\",\"score\":" + beadScore + ",\"maxScore\":4}]")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        return cycleId;
    }

    private long childId() throws Exception {
        String resp = mvc.perform(post("/api/children").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "小明", "disorderType", "ASD", "orgId", 1))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(resp).path("data").asLong();
    }

    @Test
    void 两周期阶段评估delta对比与定稿() throws Exception {
        token = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "se_teacher", "pw123456", "TEACHER");
        long cid = childId();

        // 周期1:bead=2,生成阶段评估(首期无上期)
        long c1 = setupCycleWithScore(cid, "2");
        mvc.perform(post("/api/training-cycles/" + c1 + "/stage-eval").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.deltaSummary").value(org.hamcrest.Matchers.containsString("\"previous\":null")));

        // 周期2:bead=4,生成阶段评估(对比周期1 delta=+2 达标)
        long c2 = setupCycleWithScore(cid, "4");
        String r2 = mvc.perform(post("/api/training-cycles/" + c2 + "/stage-eval").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deltaSummary").value(org.hamcrest.Matchers.containsString("\"delta\":2")))
            .andExpect(jsonPath("$.data.deltaSummary").value(org.hamcrest.Matchers.containsString("\"reached\":true")))
            .andReturn().getResponse().getContentAsString();
        long evalId = json.readTree(r2).path("data").path("id").asLong();

        // 定稿
        mvc.perform(post("/api/training-cycles/stage-evals/" + evalId + "/finalize").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FINALIZED"));

        // 定稿后不可编辑
        mvc.perform(put("/api/training-cycles/stage-evals/" + evalId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("draft", "改"))))
            .andExpect(status().isBadRequest());

        // 纵向对比:两周期评估
        mvc.perform(get("/api/training-cycles/compare").param("childId", String.valueOf(cid))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2));
    }
}
