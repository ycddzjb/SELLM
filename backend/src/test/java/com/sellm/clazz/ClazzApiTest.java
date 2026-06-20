package com.sellm.clazz;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClazzApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private ClazzRepository clazzRepo;

    private long createClass(String token, String name, String disorderTypes) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("disorderTypes", disorderTypes);
        String resp = mvc.perform(post("/api/classes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").isNumber())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).path("data").asLong();
    }

    @Test
    void 管理员建本机构班级并在列表中可见() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "cls_mgr1", "secret123", "MANAGER", 10L);
        long id = createClass(token, "小班A", "ASD");

        // 班级 orgId 自动取管理员本机构(10)
        assertThat(clazzRepo.findById(id).getOrgId()).isEqualTo(10L);

        mvc.perform(get("/api/classes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.id == " + id + ")]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.id == " + id + ")].orgId").value(org.hamcrest.Matchers.hasItem(10)));
    }

    @Test
    void 管理员改删本机构班级成功() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "cls_mgr2", "secret123", "MANAGER", 11L);
        long id = createClass(token, "中班B", "ADHD");

        Map<String, Object> upd = new HashMap<>();
        upd.put("name", "中班B改");
        upd.put("disorderTypes", "LANGUAGE");
        mvc.perform(put("/api/classes/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(upd)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"));
        assertThat(clazzRepo.findById(id).getName()).isEqualTo("中班B改");

        mvc.perform(delete("/api/classes/" + id)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        assertThat(clazzRepo.findById(id)).isNull();
    }

    @Test
    void 管理员改他机构班级返回403() throws Exception {
        // 他机构(org=99)班级:直接经 repository 造
        Clazz other = clazzRepo.save(new Clazz(null, "他机构班级", 99L, "ASD"));

        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "cls_mgr3", "secret123", "MANAGER", 12L);

        Map<String, Object> upd = new HashMap<>();
        upd.put("name", "越权改");
        upd.put("disorderTypes", "ASD");
        mvc.perform(put("/api/classes/" + other.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(upd)))
            .andExpect(status().isForbidden());

        // 数据未被改动
        assertThat(clazzRepo.findById(other.getId()).getName()).isEqualTo("他机构班级");
    }

    @Test
    void 管理员删他机构班级返回403() throws Exception {
        Clazz other = clazzRepo.save(new Clazz(null, "他机构班级2", 98L, "ADHD"));

        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "cls_mgr4", "secret123", "MANAGER", 13L);

        mvc.perform(delete("/api/classes/" + other.getId())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());

        // 数据仍在
        assertThat(clazzRepo.findById(other.getId())).isNotNull();
    }

    @Test
    void 建班级带非法障碍类型码返回400() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "cls_mgr5", "secret123", "MANAGER", 14L);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "非法码班级");
        body.put("disorderTypes", "ASD,NOPE");
        mvc.perform(post("/api/classes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 班级多选障碍类型存取一致() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "cls_mgr6", "secret123", "MANAGER", 15L);
        long id = createClass(token, "多选班级", "ASD,LANGUAGE");

        assertThat(clazzRepo.findById(id).getDisorderTypes()).isEqualTo("ASD,LANGUAGE");

        mvc.perform(get("/api/classes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.id == " + id + ")].disorderTypes")
                .value(org.hamcrest.Matchers.hasItem("ASD,LANGUAGE")));
    }

    @Test
    void 老师建班级返回403() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "cls_teacher1", "secret123", "TEACHER", 16L);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "老师建班级");
        body.put("disorderTypes", "ASD");
        mvc.perform(post("/api/classes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }
}
