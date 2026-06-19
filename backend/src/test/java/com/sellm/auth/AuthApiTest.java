package com.sellm.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.support.AuthTestSupport;
import com.sellm.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepo;

    @Test
    void 注册家长成功带机构() throws Exception {
        jdbc.update("DELETE FROM app_user WHERE username = 'api_t1'");

        // 公开注册:必须带 orgId,产 PENDING 家长,返回 id
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "api_t1", "password", "secret123", "orgId", 1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    void 待审家长登录被拒() throws Exception {
        jdbc.update("DELETE FROM app_user WHERE username = 'api_t1b'");

        // 注册一个带机构的家长 → PENDING
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "api_t1b", "password", "secret123", "orgId", 1))))
            .andExpect(status().isOk());

        // PENDING 家长登录应被拒(400 待审核)
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "api_t1b", "password", "secret123"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 注册缺机构被拒() throws Exception {
        jdbc.update("DELETE FROM app_user WHERE username = 'api_t1c'");

        // orgId 为空 → 400 请选择所属机构
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "api_t1c", "password", "secret123"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void ACTIVE账号能正常登录拿token() throws Exception {
        jdbc.update("DELETE FROM app_user WHERE username = 'api_active1'");

        // 经 UserRepository 直接造 ACTIVE 账号(模拟上级创建/种子),应能登录
        AuthTestSupport.registerAndLogin(mvc, json, userRepo,
            "api_active1", "secret123", "TEACHER", 1L);

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "api_active1", "password", "secret123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andExpect(jsonPath("$.data.role").value("TEACHER"));
    }

    @Test
    void 密码错误登录失败() throws Exception {
        jdbc.update("DELETE FROM app_user WHERE username = 'api_t2'");
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "api_t2", "password", "right", "orgId", 1))))
            .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "username", "api_t2", "password", "wrong"))))
            .andExpect(status().isBadRequest());
    }
}
