package com.sellm.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.clazz.Clazz;
import com.sellm.clazz.ClazzRepository;
import com.sellm.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeacherClassApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private ClazzRepository clazzRepo;
    @Autowired
    private TeacherClassMapper teacherClassMapper;

    @Test
    void 管理员建老师绑本机构班级成功() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "tc_mgr1", "secret123", "MANAGER", 30L);
        // 本机构(30)两个班级
        Clazz c1 = clazzRepo.save(new Clazz(null, "TC班1", 30L, "ASD"));
        Clazz c2 = clazzRepo.save(new Clazz(null, "TC班2", 30L, "ADHD"));

        Map<String, Object> body = new HashMap<>();
        body.put("username", "tc_teacher1");
        body.put("password", "secret123");
        body.put("role", "TEACHER");
        body.put("classIds", List.of(c1.getId(), c2.getId()));

        String resp = mvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andReturn().getResponse().getContentAsString();
        long teacherId = json.readTree(resp).path("data").asLong();

        assertThat(teacherClassMapper.findClassIdsByTeacher(teacherId))
            .containsExactlyInAnyOrder(c1.getId(), c2.getId());
    }

    @Test
    void 管理员建老师绑他机构班级返回403且不落关联() throws Exception {
        // 他机构(99)的班级
        Clazz other = clazzRepo.save(new Clazz(null, "他机构班", 99L, "ASD"));

        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "tc_mgr2", "secret123", "MANAGER", 31L);

        Map<String, Object> body = new HashMap<>();
        body.put("username", "tc_teacher2");
        body.put("password", "secret123");
        body.put("role", "TEACHER");
        body.put("classIds", List.of(other.getId()));

        mvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isForbidden());

        // 事务回滚:老师账号不应残留
        assertThat(userRepo.findByUsername("tc_teacher2")).isNull();
    }
}
