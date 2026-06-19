package com.sellm.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.security.Role;
import com.sellm.user.UserRepository;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public final class AuthTestSupport {

    private AuthTestSupport() {}

    /**
     * 造种子账号(任意角色)并登录,orgId 默认 1,返回 JWT。
     * 公开注册端点改造后只产 PARENT,故测试种子直接经 UserRepository 落库(绕过公开端点),
     * 再走 HTTP login 拿 token。
     */
    public static String registerAndLogin(MockMvc mvc, ObjectMapper json, UserRepository userRepo,
                                          String username, String password, String role) throws Exception {
        return registerAndLogin(mvc, json, userRepo, username, password, role, 1L);
    }

    /** 造种子账号(任意角色,指定 orgId 可为 null)并登录,返回 JWT。 */
    public static String registerAndLogin(MockMvc mvc, ObjectMapper json, UserRepository userRepo,
                                          String username, String password, String role, Long orgId) throws Exception {
        // 已存在则跳过(用唯一用户名时通常不会命中),避免 UNIQUE 约束异常
        if (userRepo.findByUsername(username) == null) {
            userRepo.register(username, password, Role.valueOf(role), orgId);
        }
        Map<String, Object> login = new HashMap<>();
        login.put("username", username);
        login.put("password", password);
        String body = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(login)))
            .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.path("data").path("token").asText();
    }
}
