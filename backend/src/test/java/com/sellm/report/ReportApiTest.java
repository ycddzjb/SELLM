package com.sellm.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.UserRepository;
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
class ReportApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;

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
    void 生成报告草稿并定稿() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "rep_teacher", "pw123456", "TEACHER");
        long childId = createChild(token, "小明");
        long assessmentId = submitAssessment(token, childId);

        // 生成报告草稿
        String rb = mvc.perform(post("/api/reports").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("assessmentId", assessmentId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.draft").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        long reportId = json.readTree(rb).path("data").path("id").asLong();

        // 定稿
        mvc.perform(put("/api/reports/" + reportId + "/finalize").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("content", "老师定稿内容"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FINALIZED"))
            .andExpect(jsonPath("$.data.finalizedContent").value("老师定稿内容"));
    }

    private long createChild(String token, String name) throws Exception {
        String cb = mvc.perform(post("/api/children").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name",name,"disorderType","ASD","orgId",1))))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(cb).path("data").asLong();
    }

    private long submitAssessment(String token, long childId) throws Exception {
        String ab = mvc.perform(post("/api/assessments").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId",childId,"scaleId","cars",
                    "answers", List.of(Map.of("itemId","q1","score",2), Map.of("itemId","q2","score",3))))))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(ab).path("data").path("id").asLong();
    }
}
