package com.sellm.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReliabilityApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void 计算信效度返回结果并落库() throws Exception {
        Map<String, Object> body = Map.of("scores",
            new int[][]{{5,4,3},{4,4,3},{3,2,2},{2,1,1}});
        mvc.perform(post("/api/research/reliability")
                .header("X-User-Id", "7").contentType("application/json")
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").isNumber())
            .andExpect(jsonPath("$.data.result.alpha").isNumber())
            .andExpect(jsonPath("$.data.result.itemCount").value(3))
            .andExpect(jsonPath("$.data.result.subjectCount").value(4));
    }

    @Test
    void 非法矩阵返400() throws Exception {
        Map<String, Object> body = Map.of("scores", new int[][]{{5,4,3},{4,3}}); // 非矩形
        mvc.perform(post("/api/research/reliability")
                .header("X-User-Id", "7").contentType("application/json")
                .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 他人信效度记录403() throws Exception {
        var res = mvc.perform(post("/api/research/reliability")
                .header("X-User-Id", "7").contentType("application/json")
                .content(json.writeValueAsString(Map.of("scores", new int[][]{{5,4,3},{2,1,1}}))))
            .andReturn();
        long id = json.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(get("/api/research/reliability/" + id).header("X-User-Id", "8"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 缺X_User_Id返401() throws Exception {
        mvc.perform(post("/api/research/reliability")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("scores", new int[][]{{1,2},{3,4}}))))
            .andExpect(status().isUnauthorized());
    }
}
