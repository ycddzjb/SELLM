package com.sellm.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssessmentApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedScale() {
        jdbc.update("DELETE FROM score_band");
        jdbc.update("DELETE FROM scale_item");
        jdbc.update("DELETE FROM scale");
        jdbc.update("INSERT INTO scale(scale_id,name,version) VALUES ('cars','CARS','v1')");
        jdbc.update("INSERT INTO scale_item(scale_id,item_id,stem,dimension) VALUES ('cars','q1','社交','社交')");
        jdbc.update("INSERT INTO scale_item(scale_id,item_id,stem,dimension) VALUES ('cars','q2','沟通','沟通')");
        jdbc.update("INSERT INTO score_band(scale_id,lower_bound,upper_bound,label,interpretation) VALUES ('cars',0,3,'正常','未见明显异常')");
        jdbc.update("INSERT INTO score_band(scale_id,lower_bound,upper_bound,label,interpretation) VALUES ('cars',4,7,'轻-中度','建议进一步评估')");
    }

    @Test
    void 老师提交评估得到计分结果且落库() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, "asm_teacher", "pw123456", "TEACHER");
        // 建档
        String cb = mvc.perform(post("/api/children").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name","小明","disorderType","ASD","orgId",1))))
            .andReturn().getResponse().getContentAsString();
        long childId = json.readTree(cb).path("data").asLong();

        mvc.perform(post("/api/assessments").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "childId", childId, "scaleId", "cars",
                    "answers", List.of(Map.of("itemId","q1","score",2),
                                       Map.of("itemId","q2","score",3))))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bandLabel").value("轻-中度"))
            .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    void 家长提交评估被拒403() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, "asm_parent", "pw123456", "PARENT");
        mvc.perform(post("/api/assessments").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "childId", 1, "scaleId", "cars",
                    "answers", List.of(Map.of("itemId","q1","score",2))))))
            .andExpect(status().isForbidden());
    }
}
