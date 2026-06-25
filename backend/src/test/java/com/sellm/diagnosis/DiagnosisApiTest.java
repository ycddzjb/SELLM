package com.sellm.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.PromptRequest;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiagnosisApiTest {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",
            () -> "jdbc:h2:mem:diagnosis_api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;

    @TestConfiguration
    static class StubAi {
        /** 产符合分隔符格式的响应,验证结构化拆分。 */
        @Bean @Primary
        AiGateway stubGateway() {
            return (PromptRequest req) ->
                "===维度结构===\n动作: 中度缺陷 | 精细动作弱 | 剥珠正确率低\n语言沟通: 轻度缺陷 | 表达少 | 词汇不足\n"
                + "===诊断报告===\n该儿童在精细动作与语言沟通方面需重点干预。";
        }
    }

    @BeforeEach
    void seedScale() {
        jdbc.update("DELETE FROM scale_item");
        jdbc.update("DELETE FROM scale");
        jdbc.update("INSERT INTO scale(scale_id,name,version,disorder_type) VALUES ('cars','CARS','v1','ASD')");
        jdbc.update("INSERT INTO scale_item(scale_id,item_id,stem,dimension) VALUES ('cars','q1','社交','社交')");
    }

    private String teacherToken() throws Exception {
        return AuthTestSupport.registerAndLogin(mvc, json, userRepository, "diag_teacher", "pw123456", "TEACHER");
    }

    private long createChild(String token) throws Exception {
        String resp = mvc.perform(post("/api/children").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "小明", "disorderType", "ASD", "orgId", 1))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(resp).path("data").asLong();
    }

    private long createDiagnosis(String token, long childId) throws Exception {
        String resp = mvc.perform(post("/api/diagnoses").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId", childId, "scaleId", "cars",
                    "structuredInput", "{\"剥珠正确率\":\"40%\",\"眼神互动\":\"偶尔\"}"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).path("data").path("id").asLong();
    }

    @Test
    void 建诊断挂素材生成结构化维度与报告() throws Exception {
        String token = teacherToken();
        long childId = createChild(token);
        long diagId = createDiagnosis(token, childId);

        // 挂文本素材
        mvc.perform(multipart("/api/diagnoses/" + diagId + "/media")
                .param("mediaType", "TEXT").param("noteText", "剥珠时手抖,需提示")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        // 生成
        mvc.perform(post("/api/diagnoses/" + diagId + "/generate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            // 结构化拆分:dimensions 段保留维度分隔符"|",draft 段不含分隔标记(用 ASCII 特征避免 GBK JVM 下中文字面量乱码)
            .andExpect(jsonPath("$.data.dimensions").value(org.hamcrest.Matchers.containsString("|")))
            .andExpect(jsonPath("$.data.dimensions").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("==="))))
            .andExpect(jsonPath("$.data.draft").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("==="))));
    }

    @Test
    void 编辑后定稿且定稿不可再编辑() throws Exception {
        String token = teacherToken();
        long diagId = createDiagnosis(token, createChild(token));
        mvc.perform(post("/api/diagnoses/" + diagId + "/generate").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(put("/api/diagnoses/" + diagId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("draft", "教师修订后的诊断"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.draft").value("教师修订后的诊断"));
        mvc.perform(post("/api/diagnoses/" + diagId + "/finalize").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FINALIZED"));
        mvc.perform(put("/api/diagnoses/" + diagId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("draft", "再改"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 定稿后可下载PDF未定稿不可() throws Exception {
        String token = teacherToken();
        long diagId = createDiagnosis(token, createChild(token));
        mvc.perform(post("/api/diagnoses/" + diagId + "/generate").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        // 未定稿下载 400
        mvc.perform(get("/api/diagnoses/" + diagId + "/pdf").header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/diagnoses/" + diagId + "/finalize").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(get("/api/diagnoses/" + diagId + "/pdf").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void 家长无权写诊断返403() throws Exception {
        String parent = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "diag_parent", "pw123456", "PARENT");
        mvc.perform(post("/api/diagnoses").header("Authorization", "Bearer " + parent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId", 1, "scaleId", "cars"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void 未登录返401() throws Exception {
        mvc.perform(post("/api/diagnoses").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId", 1))))
            .andExpect(status().isUnauthorized());
    }
}
