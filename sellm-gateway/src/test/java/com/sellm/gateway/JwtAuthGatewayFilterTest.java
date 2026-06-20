package com.sellm.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthGatewayFilterTest {

    private final GatewayJwtProperties props = new GatewayJwtProperties();
    {
        props.setSecret("test-secret-key-at-least-32-bytes-long-xx");
        props.setWhitelist(java.util.List.of("/api/auth/", "/actuator/health"));
    }

    @Test
    void 白名单路径无token放行() {
        JwtAuthGatewayFilter filter = new JwtAuthGatewayFilter(props);
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        GatewayFilterChain chain = e -> Mono.empty();

        Mono<Void> result = filter.filter(exchange, chain);
        result.block();
        // 放行:状态未被设为 401
        assertNotEquals(401, exchange.getResponse().getStatusCode() == null ? 0
            : exchange.getResponse().getStatusCode().value());
    }

    @Test
    void 非白名单缺token返回401() {
        JwtAuthGatewayFilter filter = new JwtAuthGatewayFilter(props);
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/teaching/lesson-plans").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        GatewayFilterChain chain = e -> Mono.empty();

        filter.filter(exchange, chain).block();
        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void 有效token注入用户上下文头() {
        // 用与 props 相同的 secret 签发 JWT，验证 filter 正确注入各下游头
        SecretKey key = Keys.hmacShaKeyFor(
                "test-secret-key-at-least-32-bytes-long-xx".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("alice")
                .claim("role", "TEACHER")
                .claim("uid", 42L)
                .claim("org", 7L)
                .signWith(key)
                .compact();

        JwtAuthGatewayFilter filter = new JwtAuthGatewayFilter(props);
        MockServerHttpRequest req = MockServerHttpRequest
                .get("/api/teaching/lesson-plans")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        // 捕获 filter 传给 chain 的 mutated exchange，以便断言注入头
        final ServerWebExchange[] captured = new ServerWebExchange[1];
        GatewayFilterChain chain = e -> { captured[0] = e; return Mono.empty(); };

        filter.filter(exchange, chain).block();

        assertNotNull(captured[0], "chain 应被调用（token 合法）");
        ServerHttpRequest fwd = captured[0].getRequest();
        assertEquals("42", fwd.getHeaders().getFirst("X-User-Id"));
        assertEquals("alice", fwd.getHeaders().getFirst("X-User-Name"));
        assertEquals("TEACHER", fwd.getHeaders().getFirst("X-User-Role"));
        assertEquals("7", fwd.getHeaders().getFirst("X-Org-Id"));
    }

    @Test
    void 路径穿越不能绕过鉴权() {
        // /api/auth/../teaching/x 规范化后为 /teaching/x，不在白名单，应返回 401
        JwtAuthGatewayFilter filter = new JwtAuthGatewayFilter(props);
        MockServerHttpRequest req = MockServerHttpRequest
                .get("/api/auth/../teaching/x")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        GatewayFilterChain chain = e -> Mono.empty();

        filter.filter(exchange, chain).block();
        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }
}
