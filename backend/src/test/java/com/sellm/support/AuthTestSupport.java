package com.sellm.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public final class AuthTestSupport {

    private AuthTestSupport() {}

    /** 注册(忽略已存在)并登录,orgId 默认 1,返回 JWT。 */
    public static String registerAndLogin(MockMvc mvc, ObjectMapper json,
                                          String username, String password, String role) throws Exception {
        return registerAndLogin(mvc, json, username, password, role, 1L);
    }

    /** 注册(忽略已存在)并登录,指定 orgId(可为 null),返回 JWT。 */
    public static String registerAndLogin(MockMvc mvc, ObjectMapper json,
                                          String username, String password, String role, Long orgId) throws Exception {
        Map<String, Object> reg = new HashMap<>();
        reg.put("username", username);
        reg.put("password", password);
        reg.put("role", role);
        reg.put("orgId", orgId);
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(reg)));
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
