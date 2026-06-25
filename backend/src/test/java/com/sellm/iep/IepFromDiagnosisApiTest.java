package com.sellm.iep;

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

/** IEP 基于诊断生成 + 结构化训练 prompt + 合规约束(集成)。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IepFromDiagnosisApiTest {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",
            () -> "jdbc:h2:mem:iep_from_diag;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;

    @TestConfiguration
    static class StubAi {
        @Bean @Primary
        AiGateway stubGateway() {
            // 草案回显含五领域结构;prompt 注入了合规约束,这里产合规内容
            return (PromptRequest req) ->
                "【动作训练】长期目标:提升精细动作。训练方式:一对一;训练频次:每日2次;训练步骤:剥珠分步法。\n"
                + "【语言训练】【社交互动】【认知培养】【生活自理】略。";
        }
    }

    private String teacher() throws Exception {
        return AuthTestSupport.registerAndLogin(mvc, json, userRepository, "iepdiag_t", "pw123456", "TEACHER");
    }

    private long createChild(String token) throws Exception {
        String resp = mvc.perform(post("/api/children").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "小明", "disorderType", "ASD", "orgId", 1))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(resp).path("data").asLong();
    }

    /** 直接落一条已生成结果的诊断(绕过多模态),供 IEP 取用。 */
    private long seedDiagnosis(long childId) {
        jdbc.update("INSERT INTO diagnosis(child_id,owner_id,scale_id,input_summary,dimensions,draft,status) "
            + "VALUES (?,?,?,?,?,?,?)",
            childId, 1L, "cars", "{\"剥珠正确率\":\"40%\"}",
            "动作: 中度缺陷 | 精细动作弱 | 剥珠慢", "该儿童动作领域需重点干预。", "DRAFT");
        return jdbc.queryForObject("SELECT MAX(id) FROM diagnosis", Long.class);
    }

    @Test
    void 基于诊断生成IEP草案为DRAFT() throws Exception {
        String token = teacher();
        long childId = createChild(token);
        long diagId = seedDiagnosis(childId);

        String resp = mvc.perform(post("/api/ieps").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("diagnosisId", diagId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.draft").value(org.hamcrest.Matchers.containsString("剥珠")))
            .andReturn().getResponse().getContentAsString();
        long iepId = json.readTree(resp).path("data").path("id").asLong();

        // 定稿
        mvc.perform(put("/api/ieps/" + iepId + "/finalize").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("content", "IEP 定稿"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FINALIZED"));
    }

    @Test
    void 既无诊断也无报告返400() throws Exception {
        String token = teacher();
        mvc.perform(post("/api/ieps").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 诊断不存在返400() throws Exception {
        String token = teacher();
        mvc.perform(post("/api/ieps").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("diagnosisId", 99999))))
            .andExpect(status().isBadRequest());
    }
}
