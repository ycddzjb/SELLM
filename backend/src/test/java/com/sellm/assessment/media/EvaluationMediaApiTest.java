package com.sellm.assessment.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.security.Role;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvaluationMediaApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepo;
    @Autowired private ChildRepository childRepo;

    @BeforeEach
    void seedScale() {
        jdbc.update("DELETE FROM score_band");
        jdbc.update("DELETE FROM scale_item");
        jdbc.update("DELETE FROM scale WHERE scale_id = 'mm_cars'");
        jdbc.update("INSERT INTO scale(scale_id,name,version) VALUES ('mm_cars','CARS','v1')");
        jdbc.update("INSERT INTO scale_item(scale_id,item_id,stem,dimension,sort_order,max_score) VALUES ('mm_cars','q1','社交','社交',1,4)");
        jdbc.update("INSERT INTO scale_item(scale_id,item_id,stem,dimension,sort_order,max_score) VALUES ('mm_cars','q2','沟通','沟通',2,4)");
    }

    private long uploadNote(String token, long childId, int expect) throws Exception {
        var res = mvc.perform(multipart("/api/children/" + childId + "/evaluation-media")
                .param("noteText", "课堂表现良好,能简单对视")
                .param("scaleId", "mm_cars")
                .param("mediaType", "NOTE")
                .header("Authorization", "Bearer " + token))
            .andReturn().getResponse();
        org.assertj.core.api.Assertions.assertThat(res.getStatus()).isEqualTo(expect);
        return expect == 200 ? json.readTree(res.getContentAsString()).path("data").asLong() : -1;
    }

    @Test
    void 老师上传笔记并识别得每指标建议() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo, "mm_teacher1", "secret123", "TEACHER", 300L);
        Child child = childRepo.save(new Child(null, "媒体娃", "ASD", 300L));
        long mediaId = uploadNote(token, child.getId(), 200);

        mvc.perform(post("/api/children/" + child.getId() + "/evaluation-media/" + mediaId + "/analyze")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[?(@.itemId == 'q1')]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.itemId == 'q2')]").isNotEmpty());
    }

    @Test
    void 老师上传图片文件存储往返() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo, "mm_teacher2", "secret123", "TEACHER", 301L);
        Child child = childRepo.save(new Child(null, "图片娃", "ASD", 301L));
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

        var res = mvc.perform(multipart("/api/children/" + child.getId() + "/evaluation-media")
                .file(file)
                .param("scaleId", "mm_cars")
                .param("mediaType", "IMAGE")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse();
        long mediaId = json.readTree(res.getContentAsString()).path("data").asLong();

        // analyze 能取回图片(noop 存储往返)并出建议
        mvc.perform(post("/api/children/" + child.getId() + "/evaluation-media/" + mediaId + "/analyze")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void 他机构老师上传返回403() throws Exception {
        Child other = childRepo.save(new Child(null, "他机构娃", "ASD", 302L));
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo, "mm_teacher3", "secret123", "TEACHER", 303L);
        uploadNote(token, other.getId(), 403);
    }

    @Test
    void 家长对自己孩子上传成功对别人孩子403() throws Exception {
        long parentId = userRepo.register("mm_parent1", "secret123", Role.PARENT, 304L, "ACTIVE").getId();
        Child mine = childRepo.save(new Child(null, "我娃", "ASD", 304L, parentId));
        Child notMine = childRepo.save(new Child(null, "别人娃", "ASD", 304L));
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo, "mm_parent1", "secret123", "PARENT", 304L);

        uploadNote(token, mine.getId(), 200);
        uploadNote(token, notMine.getId(), 403);
    }

    @Test
    void 缺文件且缺笔记返回400() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo, "mm_teacher4", "secret123", "TEACHER", 305L);
        Child child = childRepo.save(new Child(null, "空娃", "ASD", 305L));
        mvc.perform(multipart("/api/children/" + child.getId() + "/evaluation-media")
                .param("mediaType", "NOTE")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }
}
