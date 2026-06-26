package com.sellm.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 训练周期 + 多模态训练数据上传(集成)。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrainingCycleApiTest {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",
            () -> "jdbc:h2:mem:training_cycle_api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepository;

    private String teacher() throws Exception {
        return AuthTestSupport.registerAndLogin(mvc, json, userRepository, "tc_teacher", "pw123456", "TEACHER");
    }

    private long createChild(String token) throws Exception {
        String resp = mvc.perform(post("/api/children").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "小明", "disorderType", "ASD", "orgId", 1))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(resp).path("data").asLong();
    }

    private long createCycle(String token, long childId) throws Exception {
        String resp = mvc.perform(post("/api/training-cycles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId", childId, "title", "第一阶段"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).path("data").path("id").asLong();
    }

    @Test
    void 建周期seq自增() throws Exception {
        String token = teacher();
        long childId = createChild(token);
        // 第一个周期 seq=1
        mvc.perform(post("/api/training-cycles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId", childId, "title", "C1"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.seq").value(1));
        // 第二个周期 seq=2
        mvc.perform(post("/api/training-cycles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId", childId, "title", "C2"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.seq").value(2));
    }

    @Test
    void 挂文本训练数据带指标得分() throws Exception {
        String token = teacher();
        long cycleId = createCycle(token, createChild(token));
        mvc.perform(multipart("/api/training-cycles/" + cycleId + "/records")
                .param("mediaType", "TEXT")
                .param("noteText", "bead task practice")
                .param("scores", "[{\"item\":\"bead\",\"score\":3,\"maxScore\":4}]")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mediaType").value("TEXT"))
            .andExpect(jsonPath("$.data.scores").value(org.hamcrest.Matchers.containsString("bead")));
        // 列表
        mvc.perform(get("/api/training-cycles/" + cycleId + "/records").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 关闭周期后不可加训练数据() throws Exception {
        String token = teacher();
        long cycleId = createCycle(token, createChild(token));
        mvc.perform(post("/api/training-cycles/" + cycleId + "/close").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CLOSED"));
        mvc.perform(multipart("/api/training-cycles/" + cycleId + "/records")
                .param("mediaType", "TEXT").param("noteText", "late data")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 他人周期被拒403() throws Exception {
        String token = teacher();
        long cycleId = createCycle(token, createChild(token));
        String other = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "tc_other", "pw123456", "TEACHER", 2L);
        mvc.perform(get("/api/training-cycles/" + cycleId).header("Authorization", "Bearer " + other))
            .andExpect(status().isForbidden());
    }

    @Test
    void 家长无权建周期403() throws Exception {
        String parent = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "tc_parent", "pw123456", "PARENT");
        mvc.perform(post("/api/training-cycles").header("Authorization", "Bearer " + parent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId", 1))))
            .andExpect(status().isForbidden());
    }

    @Test
    void 未登录401() throws Exception {
        mvc.perform(post("/api/training-cycles").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId", 1))))
            .andExpect(status().isUnauthorized());
    }
}
