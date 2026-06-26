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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 适配性 → 新版 IEP(集成):阶段评估后据适配性建议优化出新 IEP。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NextIepApiTest {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",
            () -> "jdbc:h2:mem:next_iep_api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;

    @TestConfiguration
    static class StubAi {
        @Bean @Primary
        AiGateway stubGateway() {
            return (PromptRequest req) -> "[AI] 据阶段评估优化的新版 IEP:保留有效的剥珠训练,优化低效的社交项。";
        }
    }

    @Test
    void 据阶段评估生成新版IEP草案并关联周期() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "ni_teacher", "pw123456", "TEACHER");
        long childId = json.readTree(mvc.perform(post("/api/children").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "小明", "disorderType", "ASD", "orgId", 1))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").asLong();

        // 直接落一条诊断(带维度)供周期关联
        jdbc.update("INSERT INTO diagnosis(child_id,owner_id,scale_id,dimensions,draft,status) VALUES (?,?,?,?,?,?)",
            childId, 1L, "cars", "动作: 中度缺陷", "诊断报告", "FINALIZED");
        long diagId = jdbc.queryForObject("SELECT MAX(id) FROM diagnosis", Long.class);

        // 建周期(关联诊断)
        long cycleId = json.readTree(mvc.perform(post("/api/training-cycles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId", childId, "diagnosisId", diagId, "title", "C1"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();

        // 训练数据 + 阶段评估
        mvc.perform(multipart("/api/training-cycles/" + cycleId + "/records")
                .param("mediaType", "TEXT").param("noteText", "bead practice")
                .param("scores", "[{\"item\":\"bead\",\"score\":3,\"maxScore\":4}]")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        long evalId = json.readTree(mvc.perform(post("/api/training-cycles/" + cycleId + "/stage-eval")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(post("/api/training-cycles/stage-evals/" + evalId + "/finalize")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        // next-iep:据适配性生成新版 IEP DRAFT,关联周期
        mvc.perform(post("/api/training-cycles/" + cycleId + "/next-iep").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.cycleId").value(cycleId))
            .andExpect(jsonPath("$.data.draft").value(org.hamcrest.Matchers.containsString("剥珠")));
    }

    @Test
    void 未生成阶段评估直接next_iep返400() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "ni_teacher2", "pw123456", "TEACHER");
        long childId = json.readTree(mvc.perform(post("/api/children").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "小红", "disorderType", "ASD", "orgId", 1))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").asLong();
        long cycleId = json.readTree(mvc.perform(post("/api/training-cycles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId", childId, "title", "C1"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(post("/api/training-cycles/" + cycleId + "/next-iep").header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }
}
