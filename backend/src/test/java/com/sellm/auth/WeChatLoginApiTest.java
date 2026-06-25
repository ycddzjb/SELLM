package com.sellm.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.security.Role;
import com.sellm.user.AppUser;
import com.sellm.user.UserRepository;
import com.sellm.user.wechat.WeChatClient;
import com.sellm.user.wechat.WeChatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WeChatLoginApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepo;
    @Autowired StubWeChatClient stub;

    @TestConfiguration
    static class Cfg {
        @Bean @Primary StubWeChatClient stubWeChatClient(WeChatProperties props) {
            return new StubWeChatClient(props);
        }
    }

    /** 子类化覆写 send,注入假 openid,不真连微信。 */
    static class StubWeChatClient extends WeChatClient {
        volatile String openid = "oph_test_001";
        StubWeChatClient(WeChatProperties props) { super(props); }
        @Override public String openidByCode(String code) { return openid; }
    }

    private String wechatLogin(String code) throws Exception {
        return mvc.perform(post("/api/auth/wechat-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", code))))
            .andReturn().getResponse().getContentAsString();
    }

    @Test
    void 首次微信登录建PENDING家长且待审核被拦() throws Exception {
        stub.openid = "openid_new_" + System.nanoTime();
        // 新建账号为 PENDING,非 ACTIVE 被拦(400);消息走 ErrorCode 默认文案
        mvc.perform(post("/api/auth/wechat-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", "anycode"))))
            .andExpect(status().isBadRequest());
        // 已落库为 PENDING 家长,绑定 openid
        AppUser u = userRepo.findByOpenid(stub.openid);
        assertNotNull(u);
        assertEquals(Role.PARENT, u.getRole());
        assertEquals("PENDING", u.getStatus());
    }

    @Test
    void 已激活微信用户登录返token() throws Exception {
        String openid = "openid_active_" + System.nanoTime();
        // 先建 PENDING,再激活
        AppUser u = userRepo.registerWeChat("wx_" + openid, 1L, openid);
        userRepo.updateStatus(u.getId(), "ACTIVE");
        stub.openid = openid;

        String body = wechatLogin("anycode");
        var node = json.readTree(body);
        assertEquals("0", node.path("code").asText());
        assertFalse(node.path("data").path("token").asText().isBlank());
        assertEquals("PARENT", node.path("data").path("role").asText());
    }

    @Test
    void 同一openid二次登录复用账号不重复建() throws Exception {
        String openid = "openid_reuse_" + System.nanoTime();
        AppUser u = userRepo.registerWeChat("wx_" + openid, 1L, openid);
        userRepo.updateStatus(u.getId(), "ACTIVE");
        stub.openid = openid;

        wechatLogin("code1");
        wechatLogin("code2");
        // 仍是同一账号(findByOpenid 唯一)
        assertEquals(u.getId(), userRepo.findByOpenid(openid).getId());
    }

    @Test
    void 缺code返400() throws Exception {
        mvc.perform(post("/api/auth/wechat-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", ""))))
            .andExpect(status().isBadRequest());
    }
}
