package com.sellm.org;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
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
    void 超管一体建机构含管理员成功且管理员可登录() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_sa1", "secret123", "SUPER_ADMIN", null);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "星星康复中心");
        body.put("disorderTypes", "ASD,ADHD");
        body.put("province", "江苏省");
        body.put("city", "南京市");
        body.put("managerUsername", "star_mgr");
        body.put("managerPassword", "mgrpass123");

        String resp = mvc.perform(post("/api/orgs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").isNumber())
            .andReturn().getResponse().getContentAsString();
        long newOrgId = json.readTree(resp).path("data").asLong();

        // 该管理员能登录(ACTIVE),role MANAGER、orgId 指向新机构
        String loginBody = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "star_mgr", "password", "mgrpass123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andExpect(jsonPath("$.data.role").value("MANAGER"))
            .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(loginBody);
        org.assertj.core.api.Assertions.assertThat(node.path("data").path("orgId").asLong())
            .isEqualTo(newOrgId);
    }

    @Test
    void 缺管理员账号密码建机构返回400() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_sa_miss", "secret123", "SUPER_ADMIN", null);

        mvc.perform(post("/api/orgs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "name", "缺管理员机构", "region", "上海"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 管理员用户名已存在建机构返回400() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_sa_dup", "secret123", "SUPER_ADMIN", null);
        // 先占用一个用户名
        AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "dup_mgr", "secret123", "TEACHER", 1L);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "重名管理员机构");
        body.put("managerUsername", "dup_mgr");
        body.put("managerPassword", "mgrpass123");

        mvc.perform(post("/api/orgs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 非超管建机构被拒() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_mgr1", "secret123", "MANAGER", 1L);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "无权机构");
        body.put("region", "上海");
        body.put("managerUsername", "no_perm_mgr");
        body.put("managerPassword", "mgrpass123");

        mvc.perform(post("/api/orgs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }

    @Test
    void 超管看所有机构返回列表含扩字段() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "org_sa2", "secret123", "SUPER_ADMIN", null);
        // 造一个带扩字段的机构
        orgRepo.save(new Organization(null, "扩字段机构", "杭州", "ASD,LANGUAGE", "浙江省", "杭州市"));

        mvc.perform(get("/api/orgs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].name").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.disorderTypes == 'ASD,LANGUAGE')]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.province == '浙江省')]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.city == '杭州市')]").isNotEmpty());
    }

    @Test
    void 公开列表也含扩字段() throws Exception {
        orgRepo.save(new Organization(null, "公开扩字段机构", "成都", "ADHD", "四川省", "成都市"));

        mvc.perform(get("/api/orgs/public"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.disorderTypes == 'ADHD')]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.province == '四川省')]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.city == '成都市')]").isNotEmpty());
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
