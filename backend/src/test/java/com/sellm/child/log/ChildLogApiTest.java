package com.sellm.child.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.security.Role;
import com.sellm.support.AuthTestSupport;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChildLogApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private ChildRepository childRepo;

    private long addLog(String token, long childId, String type, String content, int expectStatus) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("logType", type);
        body.put("content", content);
        var res = mvc.perform(post("/api/children/" + childId + "/logs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andReturn().getResponse();
        org.assertj.core.api.Assertions.assertThat(res.getStatus()).isEqualTo(expectStatus);
        return expectStatus == 200 ? json.readTree(res.getContentAsString()).path("data").asLong() : -1;
    }

    @Test
    void 老师对本机构儿童加三类记录并按type过滤() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "cl_teacher1", "secret123", "TEACHER", 60L);
        Child child = childRepo.save(new Child(null, "记录娃", "ASD", 60L));

        addLog(token, child.getId(), "CLASSROOM_TRACK", "今日专注度好", 200);
        addLog(token, child.getId(), "HOME_COMMUNICATION", "与家长沟通作息", 200);
        addLog(token, child.getId(), "STAGE_REVIEW", "本月进步明显", 200);

        // 全部 3 条
        mvc.perform(get("/api/children/" + child.getId() + "/logs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(3));
        // 按 type 过滤 1 条,带中文标签
        mvc.perform(get("/api/children/" + child.getId() + "/logs")
                .param("type", "CLASSROOM_TRACK")
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].logTypeLabel").value("课堂追踪"));
    }

    @Test
    void 老师对他机构儿童加记录403() throws Exception {
        Child other = childRepo.save(new Child(null, "他机构娃", "ASD", 61L));
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "cl_teacher2", "secret123", "TEACHER", 62L);
        addLog(token, other.getId(), "CLASSROOM_TRACK", "越权", 403);
    }

    @Test
    void 家长对自己孩子加记录成功对别人孩子403() throws Exception {
        // 家长账号 + 自己的孩子(guardian 指向家长)
        long parentId = userRepo.register("cl_parent1", "secret123", Role.PARENT, 63L, "ACTIVE").getId();
        Child mine = childRepo.save(new Child(null, "我娃", "ASD", 63L, parentId));
        Child notMine = childRepo.save(new Child(null, "别人娃", "ASD", 63L));

        String ptoken = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "cl_parent1", "secret123", "PARENT", 63L);

        addLog(ptoken, mine.getId(), "HOME_COMMUNICATION", "在家训练记录", 200);
        addLog(ptoken, notMine.getId(), "HOME_COMMUNICATION", "越权", 403);
    }

    @Test
    void 非法记录类型400() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "cl_teacher3", "secret123", "TEACHER", 64L);
        Child child = childRepo.save(new Child(null, "娃", "ASD", 64L));
        addLog(token, child.getId(), "NOPE", "x", 400);
    }

    @Test
    void 删除本机构儿童记录() throws Exception {
        String token = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "cl_teacher4", "secret123", "TEACHER", 65L);
        Child child = childRepo.save(new Child(null, "删记录娃", "ASD", 65L));
        long logId = addLog(token, child.getId(), "STAGE_REVIEW", "待删", 200);

        mvc.perform(delete("/api/children/" + child.getId() + "/logs/" + logId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mvc.perform(get("/api/children/" + child.getId() + "/logs")
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.data.length()").value(0));
    }
}
