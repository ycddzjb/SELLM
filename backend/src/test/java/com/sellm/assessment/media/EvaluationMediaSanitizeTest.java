package com.sellm.assessment.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.multimodal.ItemSuggestion;
import com.sellm.multimodal.MultimodalModel;
import com.sellm.scale.ScaleItem;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-3 回归:vision 多模态识别前,训练笔记(noteText)必须脱敏后才出网。
 * 原 bug:Controller 把 media.getNoteText() 原文透传给 model,手机/身份证/姓名明文出网。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvaluationMediaSanitizeTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepo;
    @Autowired ChildRepository childRepo;
    @Autowired CapturingModel model;

    @TestConfiguration
    static class Cfg {
        @Bean @Primary CapturingModel capturingModel() { return new CapturingModel(); }
    }

    /** 捕获传给模型的 noteText(即将出网的文本),供断言已脱敏。 */
    static class CapturingModel implements MultimodalModel {
        volatile String receivedNote;
        @Override public List<ItemSuggestion> analyze(byte[] media, String noteText, List<ScaleItem> items) {
            this.receivedNote = noteText;
            return List.of(new ItemSuggestion("q1", 2.0, "ok"));
        }
    }

    @BeforeEach
    void seedScale() {
        jdbc.update("DELETE FROM scale_item WHERE scale_id='mm_san'");
        jdbc.update("DELETE FROM scale WHERE scale_id='mm_san'");
        jdbc.update("INSERT INTO scale(scale_id,name,version) VALUES ('mm_san','CARS','v1')");
        jdbc.update("INSERT INTO scale_item(scale_id,item_id,stem,dimension,sort_order,max_score) VALUES ('mm_san','q1','社交','社交',1,4)");
    }

    @Test
    void 训练笔记中的手机号出网前被脱敏() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo, "mm_san_t", "secret123", "TEACHER", 700L);
        Child child = childRepo.save(new Child(null, "笔记娃", "ASD", 700L));

        var res = mvc.perform(multipart("/api/children/" + child.getId() + "/evaluation-media")
                .param("noteText", "家长电话 13800138000,孩子社交进步")
                .param("scaleId", "mm_san")
                .param("mediaType", "NOTE")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse();
        long mediaId = json.readTree(res.getContentAsString()).path("data").asLong();

        mvc.perform(post("/api/children/" + child.getId() + "/evaluation-media/" + mediaId + "/analyze")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        assertThat(model.receivedNote).isNotNull();
        assertThat(model.receivedNote)
            .as("出网给 vision 模型的笔记不应含明文手机号")
            .doesNotContain("13800138000");
    }
}
