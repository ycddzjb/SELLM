package com.sellm.iep;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.report.ReportRecord;
import com.sellm.report.ReportRecordRepository;
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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FamilyIepApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository userRepo;
    @Autowired private ChildRepository childRepo;
    @Autowired private ReportRecordRepository reportRepo;

    @Test
    void 家长对有定稿报告的自己孩子生成家庭IEP成功() throws Exception {
        long orgId = 200L;
        long parentId = userRepo.register("fi_parent1", "secret123", Role.PARENT, orgId, "ACTIVE").getId();
        Child child = childRepo.save(new Child(null, "家娃", "ASD", orgId, parentId));
        // 造一条定稿报告
        reportRepo.save(new ReportRecord(null, 1L, child.getId(), "草稿", "定稿评估结论", "FINALIZED"));

        String ptoken = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "fi_parent1", "secret123", "PARENT", orgId);

        String resp = mvc.perform(post("/api/family-ieps")
                .header("Authorization", "Bearer " + ptoken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId", child.getId(), "parentGoal", "提升居家社交"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.draft").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        long famId = json.readTree(resp).path("data").path("id").asLong();

        // 定稿
        mvc.perform(put("/api/family-ieps/" + famId + "/finalize")
                .header("Authorization", "Bearer " + ptoken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("content", "家长定稿家庭计划"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FINALIZED"));
    }

    @Test
    void 无定稿报告时生成返回400() throws Exception {
        long orgId = 201L;
        long parentId = userRepo.register("fi_parent2", "secret123", Role.PARENT, orgId, "ACTIVE").getId();
        Child child = childRepo.save(new Child(null, "无报告娃", "ASD", orgId, parentId));

        String ptoken = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "fi_parent2", "secret123", "PARENT", orgId);

        mvc.perform(post("/api/family-ieps")
                .header("Authorization", "Bearer " + ptoken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId", child.getId(), "parentGoal", "x"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 家长对别人孩子生成返回403() throws Exception {
        long orgId = 202L;
        Child notMine = childRepo.save(new Child(null, "别人娃", "ASD", orgId, null));
        reportRepo.save(new ReportRecord(null, 1L, notMine.getId(), "草稿", "结论", "FINALIZED"));

        String ptoken = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "fi_parent3", "secret123", "PARENT", orgId);

        mvc.perform(post("/api/family-ieps")
                .header("Authorization", "Bearer " + ptoken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("childId", notMine.getId(), "parentGoal", "x"))))
            .andExpect(status().isForbidden());
    }
}
