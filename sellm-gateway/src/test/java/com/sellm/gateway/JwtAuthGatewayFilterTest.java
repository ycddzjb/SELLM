package com.sellm.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URI;
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

    @Test
    void 编码点穿越不能绕过鉴权() {
        // /api/auth/%2e%2e/teaching/x — 百分比编码点。
        // MockServerHttpRequest.get(String) 内部会二次编码 % → %25，导致路径语义改变。
        // 使用 URI 对象直接构造，保留原始 %2e%2e，验证 filter 能解码并规范化，结果不在白名单，返回 401。
        JwtAuthGatewayFilter filter = new JwtAuthGatewayFilter(props);
        URI uri = URI.create("/api/auth/%2e%2e/teaching/x");
        MockServerHttpRequest req = MockServerHttpRequest
                .method(HttpMethod.GET, uri)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        GatewayFilterChain chain = e -> Mono.empty();

        filter.filter(exchange, chain).block();
        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void 客户端伪造用户头被剥离() {
        // 白名单路径 /api/auth/login，客户端附带伪造的 X-User-Id: 999 无 token
        // 转发给 chain 的 exchange 中 X-User-Id 必须为 null/空（已被剥离）
        JwtAuthGatewayFilter filter = new JwtAuthGatewayFilter(props);
        MockServerHttpRequest req = MockServerHttpRequest
                .get("/api/auth/login")
                .header("X-User-Id", "999")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        final ServerWebExchange[] captured = new ServerWebExchange[1];
        GatewayFilterChain chain = e -> { captured[0] = e; return Mono.empty(); };

        filter.filter(exchange, chain).block();

        assertNotNull(captured[0], "白名单路径应放行并调用 chain");
        String forwarded = captured[0].getRequest().getHeaders().getFirst("X-User-Id");
        assertNull(forwarded, "伪造的 X-User-Id 头应在入口被剥离，不得传递给下游");
    }
}
