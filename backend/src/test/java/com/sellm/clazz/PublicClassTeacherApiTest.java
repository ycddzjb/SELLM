package com.sellm.clazz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.TeacherClassMapper;
import com.sellm.user.UserRepository;
import com.sellm.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sellm.user.AppUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicClassTeacherApiTest {

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
    void 公开查机构班级免登录() throws Exception {
        Clazz c = clazzRepo.save(new Clazz(null, "公开班级A", 70L, "ASD"));
        mvc.perform(get("/api/orgs/public/70/classes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data[?(@.id == " + c.getId() + ")].name")
                .value(org.hamcrest.Matchers.hasItem("公开班级A")));
    }

    @Test
    void 公开查班级下老师免登录() throws Exception {
        Clazz c = clazzRepo.save(new Clazz(null, "公开班级B", 71L, "ASD"));
        AppUser teacher = userRepo.register("pub_teacher1", "secret123", Role.TEACHER, 71L, "ACTIVE");
        teacherClassMapper.insert(teacher.getId(), c.getId());

        mvc.perform(get("/api/classes/public/" + c.getId() + "/teachers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data[?(@.username == 'pub_teacher1')]").isNotEmpty())
            // 不暴露 passwordHash
            .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());
    }
}
