package com.sellm.iep;

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
class IepApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

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
    void 基于报告生成IEP草案并定稿() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, "iep_teacher", "pw123456", "TEACHER");
        long childId = post1("/api/children", token, Map.of("name","小明","disorderType","ASD","orgId",1), "$.data");
        long assessmentId = post1("/api/assessments", token, Map.of("childId",childId,"scaleId","cars",
            "answers", List.of(Map.of("itemId","q1","score",2), Map.of("itemId","q2","score",3))), "$.data.id");
        long reportId = post1("/api/reports", token, Map.of("assessmentId", assessmentId), "$.data.id");

        String ib = mvc.perform(post("/api/ieps").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reportId", reportId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.draft").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        long iepId = json.readTree(ib).path("data").path("id").asLong();

        mvc.perform(put("/api/ieps/" + iepId + "/finalize").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("content", "IEP 定稿"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FINALIZED"));
    }

    // 辅助:POST 后从 JSON 路径取 long(jsonPath 表达式简化为手工解析)
    private long post1(String url, String token, Map<String,Object> body, String path) throws Exception {
        String resp = mvc.perform(post(url).header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode node = json.readTree(resp).path("data");
        return path.endsWith(".id") ? node.path("id").asLong() : node.asLong();
    }
}
