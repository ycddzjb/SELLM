package com.sellm.child;

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

    @Test
    void 老师可建档并按id读出明文姓名() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, "child_teacher", "pw123456", "TEACHER", 1L);
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
        String orgAteacher = AuthTestSupport.registerAndLogin(mvc, json, "ca_teacher_A", "pw123456", "TEACHER", 1L);
        long childId = createChild(orgAteacher, "小明", null);   // 建在 org 1

        String orgBteacher = AuthTestSupport.registerAndLogin(mvc, json, "cb_teacher_B", "pw123456", "TEACHER", 2L);
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
}
