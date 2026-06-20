package com.sellm.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
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
class UserAdminApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository userRepository;

    /** 公开注册一个家长到指定机构(产 PENDING),返回其 id。 */
    private long registerPendingParent(String username, String password, long orgId) throws Exception {
        // 注册需指派本机构老师作为审核人,先造一个
        com.sellm.user.AppUser teacher = userRepository.findByUsername(username + "_t");
        long teacherId = (teacher != null) ? teacher.getId()
            : userRepository.register(username + "_t", "secret123",
                com.sellm.security.Role.TEACHER, orgId, "ACTIVE").getId();
        Map<String, Object> reg = new HashMap<>();
        reg.put("username", username);
        reg.put("password", password);
        reg.put("orgId", orgId);
        reg.put("assignedTeacherId", teacherId);
        String body = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(reg)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").asLong();
    }

    private int loginStatus(String username, String password) throws Exception {
        Map<String, Object> login = new HashMap<>();
        login.put("username", username);
        login.put("password", password);
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(login)))
            .andReturn().getResponse().getStatus();
    }

    @Test
    void MANAGER看本机构待审家长列表含新注册家长() throws Exception {
        long orgId = 31L;
        long pid = registerPendingParent("ua_pending1", "pw123456", orgId);
        String mgr = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "ua_mgr1", "pw123456", "MANAGER", orgId);

        String body = mvc.perform(get("/api/users/pending").header("Authorization", "Bearer " + mgr))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andReturn().getResponse().getContentAsString();

        JsonNode data = json.readTree(body).path("data");
        boolean found = false;
        for (JsonNode u : data) {
            if (u.path("id").asLong() == pid) {
                found = true;
                assertThat(u.path("username").asText()).isEqualTo("ua_pending1");
                assertThat(u.path("status").asText()).isEqualTo("PENDING");
                // 不暴露 passwordHash
                assertThat(u.has("passwordHash")).isFalse();
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void MANAGER审核通过后家长可登录() throws Exception {
        long orgId = 32L;
        long pid = registerPendingParent("ua_pending2", "pw123456", orgId);
        String mgr = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "ua_mgr2", "pw123456", "MANAGER", orgId);

        // 通过前不能登录
        assertThat(loginStatus("ua_pending2", "pw123456")).isEqualTo(400);

        mvc.perform(put("/api/users/" + pid + "/approve").header("Authorization", "Bearer " + mgr))
            .andExpect(status().isOk());

        // 通过后能登录
        assertThat(loginStatus("ua_pending2", "pw123456")).isEqualTo(200);
    }

    @Test
    void MANAGER拒绝后家长仍不能登录() throws Exception {
        long orgId = 33L;
        long pid = registerPendingParent("ua_pending3", "pw123456", orgId);
        String mgr = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "ua_mgr3", "pw123456", "MANAGER", orgId);

        mvc.perform(put("/api/users/" + pid + "/reject").header("Authorization", "Bearer " + mgr))
            .andExpect(status().isOk());

        assertThat(loginStatus("ua_pending3", "pw123456")).isEqualTo(400);
    }

    @Test
    void 跨机构MANAGER审核他机构待审家长被拒403() throws Exception {
        long orgA = 41L;
        long orgB = 42L;
        long pidA = registerPendingParent("ua_pending_a", "pw123456", orgA);
        // 机构B 的 MANAGER 试图审核机构A 的待审家长
        String mgrB = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "ua_mgr_b", "pw123456", "MANAGER", orgB);

        mvc.perform(put("/api/users/" + pidA + "/approve").header("Authorization", "Bearer " + mgrB))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/users/" + pidA + "/reject").header("Authorization", "Bearer " + mgrB))
            .andExpect(status().isForbidden());

        // 越权失败后,该家长仍是 PENDING,不能登录
        assertThat(loginStatus("ua_pending_a", "pw123456")).isEqualTo(400);
    }

    @Test
    void TEACHER改自己密码成功新密码可登旧密码不可登() throws Exception {
        String teacher = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "ua_teacher_pw", "oldpw123", "TEACHER", 51L);

        Map<String, Object> body = new HashMap<>();
        body.put("oldPassword", "oldpw123");
        body.put("newPassword", "newpw456");
        mvc.perform(put("/api/users/me/password").header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk());

        assertThat(loginStatus("ua_teacher_pw", "newpw456")).isEqualTo(200);
        assertThat(loginStatus("ua_teacher_pw", "oldpw123")).isEqualTo(400);
    }

    @Test
    void 改密码原密码错误返回400() throws Exception {
        String teacher = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "ua_teacher_pw2", "rightpw123", "TEACHER", 52L);

        Map<String, Object> body = new HashMap<>();
        body.put("oldPassword", "wrongpw");
        body.put("newPassword", "newpw456");
        mvc.perform(put("/api/users/me/password").header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());

        // 原密码未变,仍可用旧密码登录
        assertThat(loginStatus("ua_teacher_pw2", "rightpw123")).isEqualTo(200);
    }

    @Test
    void TEACHER访问待审列表被拒403() throws Exception {
        String teacher = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "ua_teacher_pend", "pw123456", "TEACHER", 53L);
        mvc.perform(get("/api/users/pending").header("Authorization", "Bearer " + teacher))
            .andExpect(status().isForbidden());
    }
}
