package com.sellm.aids;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void 按障碍类型推荐ASD() throws Exception {
        mvc.perform(get("/api/aids/recommendations").param("disorderType", "ASD").header("X-User-Id", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)))
            // 返回的每条都应含 ASD
            .andExpect(jsonPath("$.data[0].disorderTypes", org.hamcrest.Matchers.hasItem("ASD")));
    }

    @Test
    void 不传障碍类型返回全部() throws Exception {
        mvc.perform(get("/api/aids/recommendations").header("X-User-Id", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(6));
    }

    @Test
    void 无匹配类型返回空列表() throws Exception {
        mvc.perform(get("/api/aids/recommendations").param("disorderType", "NOSUCH").header("X-User-Id", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 缺少身份头返回401() throws Exception {
        mvc.perform(get("/api/aids/recommendations").param("disorderType", "ASD"))
            .andExpect(status().isUnauthorized());
    }
}
