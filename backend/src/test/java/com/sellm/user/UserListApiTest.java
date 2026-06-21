package com.sellm.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.profiles.active=dev",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.service-registry.auto-registration.enabled=false"
})
public class UserListApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository userRepository;

    private String superAdminToken;
    private String manager1Token;  // 机构1的管理者
    private String manager2Token;  // 机构2的管理者
    private String teacherToken;   // 普通老师(机构1)

    private Long org1Id = 1L;
    private Long org2Id = 2L;

    @BeforeEach
    public void setup() throws Exception {
        // 创建测试数据:两个机构的管理者、老师等
        // 超管已有(admin),直接登录取 token
        superAdminToken = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "admin", "admin123", "SUPER_ADMIN", null);

        // 机构1的管理者
        manager1Token = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "mgr1_list", "mgr123", "MANAGER", org1Id);

        // 机构2的管理者
        manager2Token = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "mgr2_list", "mgr123", "MANAGER", org2Id);

        // 机构1的老师(需要具有唯一用户名)
        teacherToken = AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "teacher_list_org1", "tea123", "TEACHER", org1Id);

        // 机构2的老师
        AuthTestSupport.registerAndLogin(mvc, json, userRepository,
            "teacher_list_org2", "tea123", "TEACHER", org2Id);
    }

    @Test
    public void testSuperAdminListAllUsers() throws Exception {
        // 超管 GET /api/users 应返回所有机构的用户
        MvcResult result = mvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode resp = json.readTree(body);
        JsonNode data = resp.path("data");

        assertTrue(data.isArray(), "响应应是数组");

        // 断言包含来自不同机构的用户
        boolean hasOrg1User = false;
        boolean hasOrg2User = false;

        for (JsonNode user : data) {
            Long orgId = user.path("orgId").asLong();
            if (orgId == 1L) {
                hasOrg1User = true;
            } else if (orgId == 2L) {
                hasOrg2User = true;
            }
            // 关键安全检查:响应不含 passwordHash
            assertNull(user.get("passwordHash"), "响应不应含 passwordHash 字段");
        }

        assertTrue(hasOrg1User, "超管列表应含机构1的用户");
        assertTrue(hasOrg2User, "超管列表应含机构2的用户");
    }

    @Test
    public void testManagerListOnlyOwnOrgUsers() throws Exception {
        // MANAGER 看本机构用户(机构1的管理者)
        MvcResult result = mvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + manager1Token)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode resp = json.readTree(body);
        JsonNode data = resp.path("data");

        assertTrue(data.isArray(), "响应应是数组");

        // 断言所有用户都属于机构1,不含机构2的用户
        for (JsonNode user : data) {
            Long orgId = user.path("orgId").asLong();
            assertEquals(org1Id, orgId, "MANAGER 只应看到本机构(" + org1Id + ")的用户,但看到了 orgId=" + orgId);
        }
    }

    @Test
    public void testResponseNotContainsPasswordHash() throws Exception {
        // 验证所有响应都不含 passwordHash
        MvcResult result = mvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + superAdminToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        // 直接检查 JSON 字符串不含 passwordHash 字段名
        assertFalse(body.contains("passwordHash"), "JSON 响应不应包含 passwordHash 字符串");
        assertFalse(body.contains("password_hash"), "JSON 响应不应包含 password_hash 字符串");
    }

    @Test
    public void testTeacherAccessDenied() throws Exception {
        // TEACHER 调 GET /api/users → 403
        mvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andReturn();
    }
}
