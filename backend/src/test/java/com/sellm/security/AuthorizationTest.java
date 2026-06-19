package com.sellm.security;

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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository userRepository;

    @Test
    void 无token访问受保护端点401() throws Exception {
        mvc.perform(get("/api/children")).andExpect(status().isUnauthorized());
    }

    @Test
    void 家长写child被拒403() throws Exception {
        String parent = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "authz_parent", "pw123456", "PARENT");
        mvc.perform(post("/api/children").header("Authorization","Bearer "+parent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name","x","disorderType","ASD","orgId",1))))
            .andExpect(status().isForbidden());
    }

    @Test
    void 家长读child列表放行200() throws Exception {
        String parent = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "authz_parent2", "pw123456", "PARENT");
        mvc.perform(get("/api/children").header("Authorization","Bearer "+parent))
            .andExpect(status().isOk());
    }

    @Test
    void 家长读他人孩子档案被行级拒绝403() throws Exception {
        // 老师(org1)建一个孩子,未绑定任何家长
        String teacher = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "authz_teacher_x", "pw123456", "TEACHER", 1L);
        java.util.Map<String,Object> body = new java.util.HashMap<>();
        body.put("name", "小明"); body.put("disorderType", "ASD"); body.put("guardianUserId", null);
        String cb = mvc.perform(post("/api/children").header("Authorization","Bearer "+teacher)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long childId = json.readTree(cb).path("data").asLong();

        // 家长(非该孩子监护人)读该档案 → AccessGuard 行级拒绝 403
        String parent = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "authz_parent3", "pw123456", "PARENT", 1L);
        mvc.perform(get("/api/children/" + childId).header("Authorization","Bearer "+parent))
            .andExpect(status().isForbidden());
    }

    @Test
    void 他机构管理者读本机构孩子被行级拒绝403() throws Exception {
        String mgrA = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "authz_mgr_a", "pw123456", "MANAGER", 1L);
        java.util.Map<String,Object> body = new java.util.HashMap<>();
        body.put("name", "小红"); body.put("disorderType", "ASD"); body.put("guardianUserId", null);
        String cb = mvc.perform(post("/api/children").header("Authorization","Bearer "+mgrA)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long childId = json.readTree(cb).path("data").asLong();

        String mgrB = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "authz_mgr_b", "pw123456", "MANAGER", 2L);
        mvc.perform(get("/api/children/" + childId).header("Authorization","Bearer "+mgrB))
            .andExpect(status().isForbidden());
    }
}
