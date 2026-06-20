package com.sellm.child;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendedScalesApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepo;
    @Autowired private ChildRepository childRepo;

    @BeforeEach
    void seedScales() {
        jdbc.update("DELETE FROM scale WHERE scale_id IN ('rec_asd','rec_lang')");
        jdbc.update("INSERT INTO scale(scale_id,name,version,disorder_type) VALUES ('rec_asd','ASD量表','v1','ASD')");
        jdbc.update("INSERT INTO scale(scale_id,name,version,disorder_type) VALUES ('rec_lang','语言量表','v1','LANGUAGE')");
    }

    @Test
    void 老师对ASD儿童得ASD推荐不含他类() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo, "rec_t1", "secret123", "TEACHER", 400L);
        Child child = childRepo.save(new Child(null, "推荐娃", "ASD", 400L));

        mvc.perform(get("/api/children/" + child.getId() + "/recommended-scales")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data[?(@.scaleId == 'rec_asd')]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.scaleId == 'rec_lang')]").isEmpty());
    }

    @Test
    void 障碍类型为空返回空列表() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo, "rec_t2", "secret123", "TEACHER", 401L);
        Child child = childRepo.save(new Child(null, "无类型娃", null, 401L));

        mvc.perform(get("/api/children/" + child.getId() + "/recommended-scales")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 他机构老师访问推荐403() throws Exception {
        Child other = childRepo.save(new Child(null, "他机构娃", "ASD", 402L));
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo, "rec_t3", "secret123", "TEACHER", 403L);

        mvc.perform(get("/api/children/" + other.getId() + "/recommended-scales")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }
}
