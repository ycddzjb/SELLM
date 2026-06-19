package com.sellm.child;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.UserRepository;
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
class ChildApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void 老师可建档并按id读出明文姓名() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "child_teacher", "pw123456", "TEACHER", 1L);
        long id = createChild(token, "小明", null);

        mvc.perform(get("/api/children/" + id).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("小明"))
            .andExpect(jsonPath("$.data.disorderType").value("ASD"));
    }

    @Test
    void 无token访问受保护端点返回401() throws Exception {
        mvc.perform(get("/api/children")).andExpect(status().isUnauthorized());
    }

    @Test
    void 他机构老师访问本机构档案被拒403() throws Exception {
        String orgAteacher = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "ca_teacher_A", "pw123456", "TEACHER", 1L);
        long childId = createChild(orgAteacher, "小明", null);   // 建在 org 1

        String orgBteacher = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "cb_teacher_B", "pw123456", "TEACHER", 2L);
        mvc.perform(get("/api/children/" + childId).header("Authorization", "Bearer " + orgBteacher))
            .andExpect(status().isForbidden());   // org 2 老师无权看 org 1 的孩子
    }

    private long createChild(String token, String name, Long guardianUserId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("disorderType", "ASD");
        body.put("guardianUserId", guardianUserId);
        String resp = mvc.perform(post("/api/children").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).path("data").asLong();
    }

    @Test
    void 家长可读自己监护的孩子() throws Exception {
        // 注册家长,查出其 userId
        String parentToken = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "p_self_parent", "pw123456", "PARENT", 1L);
        Long parentId = jdbc.queryForObject(
            "SELECT id FROM app_user WHERE username = ?", Long.class, "p_self_parent");

        // 老师建档并把该家长设为监护人
        String teacherToken = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "p_self_teacher", "pw123456", "TEACHER", 1L);
        long childId = createChild(teacherToken, "小明", parentId);

        // 家长读自己监护的孩子 → 200
        mvc.perform(get("/api/children/" + childId).header("Authorization", "Bearer " + parentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("小明"));
    }

    @Test
    void 家长不能读他人的孩子403() throws Exception {
        // 老师建一个未绑定该家长的孩子
        String teacherToken = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "p_other_teacher", "pw123456", "TEACHER", 1L);
        long childId = createChild(teacherToken, "小红", null);

        // 另一个家长(非监护人)读 → 403
        String parentToken = AuthTestSupport.registerAndLogin(mvc, json, userRepository, "p_other_parent", "pw123456", "PARENT", 1L);
        mvc.perform(get("/api/children/" + childId).header("Authorization", "Bearer " + parentToken))
            .andExpect(status().isForbidden());
    }
}
