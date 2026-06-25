package com.sellm.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.parent.ParentProfile;
import com.sellm.parent.ParentProfileRepository;
import com.sellm.security.Role;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.AppUser;
import com.sellm.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WeChatActivateApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepo;
    @Autowired ChildRepository childRepo;
    @Autowired ParentProfileRepository parentProfileRepo;

    private String managerToken;   // org=1 的管理者

    @BeforeEach
    void setUp() throws Exception {
        managerToken = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "mgr_wx_" + System.nanoTime(), "secret123", "MANAGER", 1L);
    }

    /** 造一个微信 PENDING 家长(org 可空),返回其 id。 */
    private long seedWeChatPending(Long orgId) {
        String openid = "openid_" + System.nanoTime();
        AppUser u = userRepo.registerWeChat("wx_" + openid, orgId, openid);
        return u.getId();
    }

    private void activate(long id, Map<String, Object> body, int expectStatus) throws Exception {
        mvc.perform(put("/api/users/" + id + "/activate-wechat")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().is(expectStatus));
    }

    @Test
    void 激活无机构微信家长落本机构并建孩子() throws Exception {
        long pid = seedWeChatPending(null);
        Map<String, Object> body = new HashMap<>();
        body.put("childName", "小明");
        body.put("childDisorderType", "ASD");
        activate(pid, body, 200);

        AppUser u = userRepo.findById(pid);
        assertEquals("ACTIVE", u.getStatus());
        assertEquals(1L, u.getOrgId());   // 认领落本机构
        // 建了孩子档案,guardian=该家长,profile.childId 回填
        ParentProfile profile = parentProfileRepo.findByUserId(pid);
        assertNotNull(profile);
        assertNotNull(profile.getChildId());
        Child child = childRepo.findById(profile.getChildId());
        assertNotNull(child);
        assertEquals(pid, child.getGuardianUserId());
        assertEquals("小明", child.getName());
        assertEquals(1L, child.getOrgId());
    }

    @Test
    void 待激活列表含本机构与无机构微信家长() throws Exception {
        seedWeChatPending(null);
        seedWeChatPending(1L);
        var res = mvc.perform(get("/api/users/pending-wechat")
                .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andReturn();
        var arr = json.readTree(res.getResponse().getContentAsString()).path("data");
        assertTrue(arr.size() >= 2);
    }

    @Test
    void 激活他机构微信家长403() throws Exception {
        long pid = seedWeChatPending(2L);   // 他机构
        Map<String, Object> body = new HashMap<>();
        body.put("childName", "小红");
        activate(pid, body, 403);
    }

    @Test
    void 非微信家长走激活端点400() throws Exception {
        // 密码注册的 PENDING 家长(无 wx_openid)
        AppUser u = userRepo.register("pwd_parent_" + System.nanoTime(), "secret123",
            Role.PARENT, 1L, "PENDING");
        Map<String, Object> body = new HashMap<>();
        body.put("childName", "小刚");
        activate(u.getId(), body, 400);
    }

    @Test
    void 已激活账号再激活400() throws Exception {
        long pid = seedWeChatPending(1L);
        userRepo.updateStatus(pid, "ACTIVE");
        Map<String, Object> body = new HashMap<>();
        body.put("childName", "小李");
        activate(pid, body, 400);
    }

    @Test
    void 缺孩子姓名400() throws Exception {
        long pid = seedWeChatPending(1L);
        activate(pid, new HashMap<>(), 400);
    }

    @Test
    void 非法障碍码400() throws Exception {
        long pid = seedWeChatPending(1L);
        Map<String, Object> body = new HashMap<>();
        body.put("childName", "小王");
        body.put("childDisorderType", "NOSUCH");
        activate(pid, body, 400);
    }

    @Test
    void 班级不属本机构403() throws Exception {
        long pid = seedWeChatPending(1L);
        Map<String, Object> body = new HashMap<>();
        body.put("childName", "小赵");
        body.put("classId", 999999L);   // 不存在/非本机构
        activate(pid, body, 403);
    }

    @Test
    void 拒绝微信家长置REJECTED() throws Exception {
        long pid = seedWeChatPending(1L);
        mvc.perform(put("/api/users/" + pid + "/reject-wechat")
                .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk());
        assertEquals("REJECTED", userRepo.findById(pid).getStatus());
    }

    @Test
    void 老师无权调激活端点403() throws Exception {
        String teacherToken = AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "tch_wx_" + System.nanoTime(), "secret123", "TEACHER", 1L);
        long pid = seedWeChatPending(1L);
        Map<String, Object> body = new HashMap<>();
        body.put("childName", "小钱");
        mvc.perform(put("/api/users/" + pid + "/activate-wechat")
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }
}
