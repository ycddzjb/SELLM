package com.sellm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    private final JwtService jwt =
        new JwtService("test-jwt-secret-key-at-least-32-bytes-long-0123456789", 120);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwt);

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void 合法token设置认证主体() throws Exception {
        String token = jwt.issue("t1", "TEACHER", 7L, 3L);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(AuthPrincipal.class);
        verify(chain).doFilter(req, res);
    }

    @Test
    void 非法role的token不认证且不抛异常() throws Exception {
        // 用一个 role 不在枚举内的 token(手工签发:role=GHOST)
        String token = jwt.issue("t1", "GHOST", 7L, 3L);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // 不应抛异常
        filter.doFilter(req, res, chain);

        // 未设置认证主体
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // 过滤链仍继续(交授权层拒)
        verify(chain).doFilter(req, res);
    }

    @Test
    void 无token不认证() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(req, res);
    }
}
