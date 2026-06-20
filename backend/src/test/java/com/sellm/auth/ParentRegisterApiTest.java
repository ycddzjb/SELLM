package com.sellm.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.parent.ParentProfile;
import com.sellm.parent.ParentProfileRepository;
import com.sellm.security.Role;
import com.sellm.user.AppUser;
import com.sellm.user.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParentRegisterApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private ParentProfileRepository parentProfileRepo;

    private long seedTeacher(String username, Long orgId) {
        AppUser e = userRepo.findByUsername(username);
        return e != null ? e.getId()
            : userRepo.register(username, "secret123", Role.TEACHER, orgId, "ACTIVE").getId();
    }

    @Test
    void 注册成功落app_user与parent_profile两表() throws Exception {
        long teacherId = seedTeacher("pr_teacher1", 80L);
        Map<String, Object> body = new HashMap<>();
        body.put("username", "pr_parent1");
        body.put("password", "secret123");
        body.put("orgId", 80L);
        body.put("assignedTeacherId", teacherId);
        body.put("name", "赵家长");
        body.put("relationship", "MOTHER_SON");
        body.put("childName", "赵小宝");
        body.put("childDisorderType", "ASD");

        String resp = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andReturn().getResponse().getContentAsString();
        long userId = json.readTree(resp).path("data").asLong();

        // app_user:PARENT + PENDING
        AppUser u = userRepo.findById(userId);
        assertThat(u.getRole()).isEqualTo(Role.PARENT);
        assertThat(u.getStatus()).isEqualTo("PENDING");
        // parent_profile:落库且姓名解密一致
        ParentProfile p = parentProfileRepo.findByUserId(userId);
        assertThat(p.getName()).isEqualTo("赵家长");
        assertThat(p.getChildName()).isEqualTo("赵小宝");
        assertThat(p.getAssignedTeacherId()).isEqualTo(teacherId);
    }

    @Test
    void 缺审核老师返回400() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", "pr_parent2");
        body.put("password", "secret123");
        body.put("orgId", 80L);
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 非法关系码返回400() throws Exception {
        long teacherId = seedTeacher("pr_teacher3", 81L);
        Map<String, Object> body = new HashMap<>();
        body.put("username", "pr_parent3");
        body.put("password", "secret123");
        body.put("orgId", 81L);
        body.put("assignedTeacherId", teacherId);
        body.put("relationship", "UNCLE"); // 非法
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 指派非本机构老师返回400() throws Exception {
        long teacherId = seedTeacher("pr_teacher4", 82L); // 老师在 org82
        Map<String, Object> body = new HashMap<>();
        body.put("username", "pr_parent4");
        body.put("password", "secret123");
        body.put("orgId", 83L);                            // 注册到 org83
        body.put("assignedTeacherId", teacherId);          // 指派他机构老师
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
        // 事务回滚:用户名不应残留
        assertThat(userRepo.findByUsername("pr_parent4")).isNull();
    }
}
