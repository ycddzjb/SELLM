package com.sellm.flow;

import com.fasterxml.jackson.databind.JsonNode;
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
class FullChainApiTest {

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
    void 登录到IEP的完整链路() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "flow_teacher", "pw123456", "TEACHER");

        long childId = dataLong(postOk("/api/children", token, Map.of("name","小明","disorderType","ASD","orgId",1)), false);
        long assessmentId = dataLong(postOk("/api/assessments", token, Map.of("childId",childId,"scaleId","cars",
            "answers", List.of(Map.of("itemId","q1","score",2), Map.of("itemId","q2","score",3)))), true);
        long reportId = dataLong(postOk("/api/reports", token, Map.of("assessmentId", assessmentId)), true);
        long iepId = dataLong(postOk("/api/ieps", token, Map.of("reportId", reportId)), true);

        // 读出 IEP,确认 DRAFT 且 draft 含原始姓名(经网关脱敏后还原)
        mvc.perform(get("/api/ieps/" + iepId).header("Authorization","Bearer "+token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.draft").value(org.hamcrest.Matchers.containsString("小明")));
    }

    private String postOk(String url, String token, Map<String,Object> body) throws Exception {
        return mvc.perform(post(url).header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private long dataLong(String respBody, boolean nestedId) throws Exception {
        JsonNode data = json.readTree(respBody).path("data");
        return nestedId ? data.path("id").asLong() : data.asLong();
    }
}
