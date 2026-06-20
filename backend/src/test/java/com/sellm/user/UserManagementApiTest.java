package com.sellm.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserManagementApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void MANAGER建TEACHER成功且新账号归属其机构() throws Exception {
        String mgr = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "um_mgr", "pw123456", "MANAGER", 7L);

        String resp = mvc.perform(post("/api/users").header("Authorization", "Bearer " + mgr)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "um_new_teacher", "password", "pw123456", "role", "TEACHER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andReturn().getResponse().getContentAsString();
        long newId = json.readTree(resp).path("data").asLong();

        // 新账号 orgId 强制 = 建者机构(7),不取请求里的任何 orgId
        Long orgId = jdbc.queryForObject(
            "SELECT org_id FROM app_user WHERE id = ?", Long.class, newId);
        org.assertj.core.api.Assertions.assertThat(orgId).isEqualTo(7L);
        String role = jdbc.queryForObject(
            "SELECT role FROM app_user WHERE id = ?", String.class, newId);
        org.assertj.core.api.Assertions.assertThat(role).isEqualTo("TEACHER");
    }

    @Test
    void 超管建MANAGER到指定机构成功且ACTIVE() throws Exception {
        String sa = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "um_super", "pw123456", "SUPER_ADMIN", null);

        String resp = mvc.perform(post("/api/users").header("Authorization", "Bearer " + sa)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "um_new_mgr", "password", "pw123456", "role", "MANAGER", "orgId", 9))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andReturn().getResponse().getContentAsString();
        long newId = json.readTree(resp).path("data").asLong();

        // 归属请求指定机构(9)、角色 MANAGER、状态 ACTIVE
        Long orgId = jdbc.queryForObject(
            "SELECT org_id FROM app_user WHERE id = ?", Long.class, newId);
        org.assertj.core.api.Assertions.assertThat(orgId).isEqualTo(9L);
        String role = jdbc.queryForObject(
            "SELECT role FROM app_user WHERE id = ?", String.class, newId);
        org.assertj.core.api.Assertions.assertThat(role).isEqualTo("MANAGER");
        String status = jdbc.queryForObject(
            "SELECT status FROM app_user WHERE id = ?", String.class, newId);
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("ACTIVE");
    }

    @Test
    void 超管未指定机构建账号返回400() throws Exception {
        String sa = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "um_super2", "pw123456", "SUPER_ADMIN", null);
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + sa)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "um_no_org", "password", "pw123456", "role", "MANAGER"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void MANAGER建PARENT成功且ACTIVE非PENDING() throws Exception {
        String mgr = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "um_mgr_p", "pw123456", "MANAGER", 7L);

        String resp = mvc.perform(post("/api/users").header("Authorization", "Bearer " + mgr)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "um_new_parent", "password", "pw123456", "role", "PARENT"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andReturn().getResponse().getContentAsString();
        long newId = json.readTree(resp).path("data").asLong();

        String status = jdbc.queryForObject(
            "SELECT status FROM app_user WHERE id = ?", String.class, newId);
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("ACTIVE");
        Long orgId = jdbc.queryForObject(
            "SELECT org_id FROM app_user WHERE id = ?", Long.class, newId);
        org.assertj.core.api.Assertions.assertThat(orgId).isEqualTo(7L);
    }

    @Test
    void MANAGER建MANAGER被业务拒403() throws Exception {
        String mgr = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "um_mgr_x", "pw123456", "MANAGER", 7L);
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + mgr)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "um_mgr_dup", "password", "pw123456", "role", "MANAGER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void MANAGER传orgId也无法越权到他机构建人() throws Exception {
        String mgr = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "um_mgr_org", "pw123456", "MANAGER", 7L);

        String resp = mvc.perform(post("/api/users").header("Authorization", "Bearer " + mgr)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "um_cross_org", "password", "pw123456", "role", "TEACHER", "orgId", 99))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long newId = json.readTree(resp).path("data").asLong();

        // 忽略请求里的 orgId=99,强制归建者机构(7)
        Long orgId = jdbc.queryForObject(
            "SELECT org_id FROM app_user WHERE id = ?", Long.class, newId);
        org.assertj.core.api.Assertions.assertThat(orgId).isEqualTo(7L);
    }

    @Test
    void TEACHER调用建账号被拒403() throws Exception {
        String teacher = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "um_teacher", "pw123456", "TEACHER", 1L);
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "um_x1", "password", "pw123456", "role", "TEACHER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void PARENT调用建账号被拒403() throws Exception {
        String parent = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "um_parent", "pw123456", "PARENT", 1L);
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + parent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "um_x2", "password", "pw123456", "role", "MANAGER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void 公开注册即使传role为MANAGER也只产PARENT无法越权读他人孩子() throws Exception {
        jdbc.update("DELETE FROM app_user WHERE username = 'um_attacker'");
        // 注册需指派本机构老师;造一个 org1 老师
        long umTeacherId = userRepository.register("um_attacker_t", "secret123",
            com.sellm.security.Role.TEACHER, 1L, "ACTIVE").getId();
        // 攻击者公开注册,试图自选 role=MANAGER、orgId=1 提权
        Map<String, Object> reg = new HashMap<>();
        reg.put("username", "um_attacker");
        reg.put("password", "pw123456");
        reg.put("role", "MANAGER");
        reg.put("orgId", 1);
        reg.put("assignedTeacherId", umTeacherId);
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(reg)))
            .andExpect(status().isOk());

        // 公开注册产 PENDING,审核通过(置 ACTIVE)后才能登录;下面验证其仍只是 PARENT、无法越权
        jdbc.update("UPDATE app_user SET status = 'ACTIVE' WHERE username = 'um_attacker'");

        // 登录拿 token,role 应为 PARENT(未提权)
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "um_attacker", "pw123456", "PARENT", null);

        // 老师在 org1 建一个未绑定该账号的孩子
        String teacher = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "um_org_teacher", "pw123456", "TEACHER", 1L);
        Map<String, Object> child = new HashMap<>();
        child.put("name", "小明");
        child.put("disorderType", "ASD");
        child.put("guardianUserId", null);
        String cb = mvc.perform(post("/api/children").header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(child)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long childId = json.readTree(cb).path("data").asLong();

        // 攻击者(实际 PARENT、非监护人)读该孩子 → 403,证明未越权
        mvc.perform(get("/api/children/" + childId).header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }
}
