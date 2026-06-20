package com.sellm.child;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChildExtendedFieldsApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository userRepo;

    @Test
    void 老师建档带扩展字段并读回() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "cx_teacher1", "secret123", "TEACHER", 1L);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "扩展娃");
        body.put("disorderType", "ASD");
        body.put("baselineSummary", "基线概要A");
        body.put("monthlyGoal", "月度目标A");
        body.put("reassessDate", "2026-10-01");
        body.put("iepDueDate", "2026-12-01");
        body.put("interventionProgress", "进行中");

        String resp = mvc.perform(post("/api/children")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long id = json.readTree(resp).path("data").asLong();

        mvc.perform(get("/api/children/" + id).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.baselineSummary").value("基线概要A"))
            .andExpect(jsonPath("$.data.monthlyGoal").value("月度目标A"))
            .andExpect(jsonPath("$.data.reassessDate").value("2026-10-01"))
            .andExpect(jsonPath("$.data.iepDueDate").value("2026-12-01"))
            .andExpect(jsonPath("$.data.interventionProgress").value("进行中"));
    }

    @Test
    void 老师改档更新扩展字段() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "cx_teacher2", "secret123", "TEACHER", 1L);
        Map<String, Object> create = new HashMap<>();
        create.put("name", "改档娃");
        create.put("disorderType", "ADHD");
        String resp = mvc.perform(post("/api/children")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(create)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long id = json.readTree(resp).path("data").asLong();

        Map<String, Object> upd = new HashMap<>();
        upd.put("name", "改档娃");
        upd.put("disorderType", "ADHD");
        upd.put("annualIepSummary", "年度方案改后");
        upd.put("reassessDate", "2027-03-01");
        mvc.perform(put("/api/children/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(upd)))
            .andExpect(status().isOk());

        mvc.perform(get("/api/children/" + id).header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.data.annualIepSummary").value("年度方案改后"))
            .andExpect(jsonPath("$.data.reassessDate").value("2027-03-01"));
    }
}
