package com.sellm.org;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private OrganizationRepository orgRepo;

    @BeforeEach
    void seedOrg() {
        // test profile 下 schema 建表但无种子机构,先插一个
        orgRepo.save(new Organization(null, "阳光小学", "北京"));
    }

    @Test
    void 公开机构列表免登录可访问() throws Exception {
        mvc.perform(get("/api/orgs/public"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].id").isNumber())
            .andExpect(jsonPath("$.data[0].name").isNotEmpty());
    }

    @Test
    void 超管建机构成功() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_sa1", "secret123", "SUPER_ADMIN", null);

        mvc.perform(post("/api/orgs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "name", "星星康复中心", "region", "南京"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    void 非超管建机构被拒() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_mgr1", "secret123", "MANAGER", 1L);

        mvc.perform(post("/api/orgs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "name", "无权机构", "region", "上海"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void 超管看所有机构返回列表() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_sa2", "secret123", "SUPER_ADMIN", null);

        mvc.perform(get("/api/orgs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].name").isNotEmpty());
    }

    @Test
    void 非超管看所有机构被拒() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_mgr2", "secret123", "MANAGER", 1L);

        mvc.perform(get("/api/orgs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }
}
