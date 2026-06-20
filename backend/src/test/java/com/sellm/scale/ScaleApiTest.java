package com.sellm.scale;

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

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScaleApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository userRepo;

    private String superAdmin(String username) throws Exception {
        return AuthTestSupport.registerAndLogin(mvc, json, userRepo, username, "secret123", "SUPER_ADMIN", null);
    }

    private Map<String, Object> scaleBody(String scaleId, String name, String disorderType, int itemCount) {
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (int i = 1; i <= itemCount; i++) {
            items.add(Map.of("itemId", "i" + i, "stem", "题" + i, "dimension", "维度",
                "sortOrder", i, "maxScore", 4));
        }
        return Map.of(
            "scaleId", scaleId, "name", name, "version", "v1",
            "disorderType", disorderType, "description", "测试量表",
            "items", items,
            "bands", List.of(
                Map.of("lowerBound", 0, "upperBound", 5, "label", "正常", "interpretation", "未见异常"),
                Map.of("lowerBound", 6, "upperBound", 20, "label", "异常", "interpretation", "建议干预")));
    }

    private void create(String token, Map<String, Object> body) throws Exception {
        mvc.perform(post("/api/scales")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void 超管创建量表并读回完整详情() throws Exception {
        String token = superAdmin("scale_sa1");
        create(token, scaleBody("si_sa1", "感统量表", "SENSORY_INTEGRATION", 3));

        mvc.perform(get("/api/scales/si_sa1").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("感统量表"))
            .andExpect(jsonPath("$.data.disorderType").value("SENSORY_INTEGRATION"))
            .andExpect(jsonPath("$.data.items.length()").value(3))
            .andExpect(jsonPath("$.data.bands.length()").value(2));
    }

    @Test
    void 超管更新量表加题目() throws Exception {
        String token = superAdmin("scale_sa2");
        create(token, scaleBody("upd_sa2", "原名", "ADHD", 2));

        Map<String, Object> upd = scaleBody("upd_sa2", "新名", "ADHD", 4);
        mvc.perform(put("/api/scales/upd_sa2")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(upd)))
            .andExpect(status().isOk());

        mvc.perform(get("/api/scales/upd_sa2").header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.data.name").value("新名"))
            .andExpect(jsonPath("$.data.items.length()").value(4));
    }

    @Test
    void 超管删除量表后详情404业务码() throws Exception {
        String token = superAdmin("scale_sa3");
        create(token, scaleBody("del_sa3", "待删", "LANGUAGE", 1));

        mvc.perform(delete("/api/scales/del_sa3").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        // 删除后查询 → 业务异常(400)
        mvc.perform(get("/api/scales/del_sa3").header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 列表按品类过滤() throws Exception {
        String token = superAdmin("scale_sa4");
        create(token, scaleBody("flt_asd", "ASD表", "ASD", 1));
        create(token, scaleBody("flt_lang", "语言表", "LANGUAGE", 1));

        mvc.perform(get("/api/scales").param("disorderType", "LANGUAGE")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.scaleId == 'flt_lang')]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.scaleId == 'flt_asd')]").isEmpty());
    }

    @Test
    void scaleId重复返回400() throws Exception {
        String token = superAdmin("scale_sa5");
        create(token, scaleBody("dup_sa5", "原", "ASD", 1));
        mvc.perform(post("/api/scales")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(scaleBody("dup_sa5", "重复", "ASD", 1))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 无效品类码返回400() throws Exception {
        String token = superAdmin("scale_sa6");
        mvc.perform(post("/api/scales")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(scaleBody("bad_sa6", "坏", "NOPE", 1))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void MANAGER创建量表返回403() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "scale_mgr1", "secret123", "MANAGER", 1L);
        mvc.perform(post("/api/scales")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(scaleBody("mgr_x", "x", "ASD", 1))))
            .andExpect(status().isForbidden());
    }

    @Test
    void TEACHER可读量表列表() throws Exception {
        String sa = superAdmin("scale_sa7");
        create(sa, scaleBody("tch_read", "可读表", "ASD", 1));

        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "scale_tch1", "secret123", "TEACHER", 1L);
        mvc.perform(get("/api/scales").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"));
    }
}
