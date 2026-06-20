package com.sellm.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.security.Role;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParentListApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository userRepo;

    /** 通过公开注册造一个带 profile 的家长(指派该机构老师)。 */
    private void registerParent(String username, long orgId, String childName) throws Exception {
        long teacherId = userRepo.register(username + "_t", "secret123", Role.TEACHER, orgId, "ACTIVE").getId();
        Map<String, Object> reg = new HashMap<>();
        reg.put("username", username);
        reg.put("password", "secret123");
        reg.put("orgId", orgId);
        reg.put("assignedTeacherId", teacherId);
        reg.put("name", username + "_name");
        reg.put("relationship", "MOTHER_SON");
        reg.put("childName", childName);
        reg.put("childDisorderType", "ASD");
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(reg)))
            .andExpect(status().isOk());
    }

    @Test
    void 管理员看本机构家长完整字段且不含他机构() throws Exception {
        registerParent("pl_parent_in", 40L, "小明");
        registerParent("pl_parent_out", 41L, "小红");

        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "pl_mgr1", "secret123", "MANAGER", 40L);

        mvc.perform(get("/api/users/parents")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data[?(@.username == 'pl_parent_in')]").isNotEmpty())
            // 完整字段:姓名/儿童姓名/关系标签
            .andExpect(jsonPath("$.data[?(@.username == 'pl_parent_in')].childName")
                .value(org.hamcrest.Matchers.hasItem("小明")))
            .andExpect(jsonPath("$.data[?(@.username == 'pl_parent_in')].relationshipLabel")
                .value(org.hamcrest.Matchers.hasItem("母子")))
            // 不含他机构家长
            .andExpect(jsonPath("$.data[?(@.username == 'pl_parent_out')]").isEmpty());
    }

    @Test
    void 老师调家长列表返回403() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "pl_teacher1", "secret123", "TEACHER", 40L);

        mvc.perform(get("/api/users/parents")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }
}
