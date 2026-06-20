package com.sellm.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.security.Role;
import com.sellm.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void 管理员看本机构家长含本机构不含他机构() throws Exception {
        // 本机构(40)家长 + 他机构(41)家长
        userRepo.register("pl_parent_in", "secret123", Role.PARENT, 40L, "ACTIVE");
        userRepo.register("pl_parent_out", "secret123", Role.PARENT, 41L, "ACTIVE");

        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "pl_mgr1", "secret123", "MANAGER", 40L);

        mvc.perform(get("/api/users/parents")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data[?(@.username == 'pl_parent_in')]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.username == 'pl_parent_out')]").isEmpty())
            // 只返回 PARENT,不含管理员自己
            .andExpect(jsonPath("$.data[?(@.username == 'pl_mgr1')]").isEmpty());
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
