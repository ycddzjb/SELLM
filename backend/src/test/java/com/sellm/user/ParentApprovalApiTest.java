package com.sellm.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.parent.ParentProfile;
import com.sellm.parent.ParentProfileRepository;
import com.sellm.security.Role;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParentApprovalApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private ParentProfileRepository parentProfileRepo;
    @Autowired
    private ChildRepository childRepo;

    /** 注册一个家长(指派给 teacherId,机构 orgId),返回家长 userId。 */
    private long registerParent(String username, long orgId, long teacherId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", "secret123");
        body.put("orgId", orgId);
        body.put("assignedTeacherId", teacherId);
        body.put("name", "家长" + username);
        body.put("relationship", "MOTHER_SON");
        body.put("childName", "儿童" + username);
        body.put("childDisorderType", "ASD");
        String resp = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).path("data").asLong();
    }

    @Test
    void 被指派老师审核通过建儿童且家长可登录() throws Exception {
        long orgId = 90L;
        long teacherId = userRepo.register("appr_teacher1", "secret123", Role.TEACHER, orgId, "ACTIVE").getId();
        String tToken = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "appr_teacher1", "secret123", "TEACHER", orgId);
        long parentId = registerParent("appr_parent1", orgId, teacherId);

        mvc.perform(put("/api/users/" + parentId + "/approve")
                .header("Authorization", "Bearer " + tToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"));

        // app_user 置 ACTIVE
        assertThat(userRepo.findById(parentId).getStatus()).isEqualTo("ACTIVE");
        // Child 已建:guardian=家长,org=家长机构,且 parent_profile.child_id 回填
        ParentProfile p = parentProfileRepo.findByUserId(parentId);
        assertThat(p.getChildId()).isNotNull();
        Child child = childRepo.findById(p.getChildId());
        assertThat(child.getGuardianUserId()).isEqualTo(parentId);
        assertThat(child.getOrgId()).isEqualTo(orgId);
        assertThat(child.getName()).isEqualTo("儿童appr_parent1"); // 解密一致

        // 家长审核通过后能登录
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "appr_parent1", "password", "secret123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").value("PARENT"));
    }

    @Test
    void 非指派老师审核返回403() throws Exception {
        long orgId = 91L;
        long teacherId = userRepo.register("appr_teacher2", "secret123", Role.TEACHER, orgId, "ACTIVE").getId();
        long parentId = registerParent("appr_parent2", orgId, teacherId);
        // 另一个老师(同机构但非指派)
        String otherToken = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "appr_teacher2b", "secret123", "TEACHER", orgId);

        mvc.perform(put("/api/users/" + parentId + "/approve")
                .header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isForbidden());
        // 仍 PENDING,未建儿童
        assertThat(userRepo.findById(parentId).getStatus()).isEqualTo("PENDING");
        assertThat(parentProfileRepo.findByUserId(parentId).getChildId()).isNull();
    }

    @Test
    void 管理员审核返回403端点级() throws Exception {
        long orgId = 92L;
        long teacherId = userRepo.register("appr_teacher3", "secret123", Role.TEACHER, orgId, "ACTIVE").getId();
        long parentId = registerParent("appr_parent3", orgId, teacherId);
        String mgrToken = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "appr_mgr3", "secret123", "MANAGER", orgId);

        mvc.perform(put("/api/users/" + parentId + "/approve")
                .header("Authorization", "Bearer " + mgrToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void 拒绝置REJECTED且不建儿童() throws Exception {
        long orgId = 93L;
        long teacherId = userRepo.register("appr_teacher4", "secret123", Role.TEACHER, orgId, "ACTIVE").getId();
        String tToken = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "appr_teacher4", "secret123", "TEACHER", orgId);
        long parentId = registerParent("appr_parent4", orgId, teacherId);

        mvc.perform(put("/api/users/" + parentId + "/reject")
                .header("Authorization", "Bearer " + tToken))
            .andExpect(status().isOk());
        assertThat(userRepo.findById(parentId).getStatus()).isEqualTo("REJECTED");
        assertThat(parentProfileRepo.findByUserId(parentId).getChildId()).isNull();
    }

    @Test
    void 老师待审列表只含分派给自己的() throws Exception {
        long orgId = 94L;
        long teacherId = userRepo.register("appr_teacher5", "secret123", Role.TEACHER, orgId, "ACTIVE").getId();
        long otherTeacherId = userRepo.register("appr_teacher5b", "secret123", Role.TEACHER, orgId, "ACTIVE").getId();
        String tToken = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "appr_teacher5", "secret123", "TEACHER", orgId);

        registerParent("appr_parent5_mine", orgId, teacherId);
        registerParent("appr_parent5_other", orgId, otherTeacherId);

        mvc.perform(get("/api/users/pending").header("Authorization", "Bearer " + tToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.username == 'appr_parent5_mine')]").isNotEmpty())
            .andExpect(jsonPath("$.data[?(@.username == 'appr_parent5_other')]").isEmpty());
    }
}
