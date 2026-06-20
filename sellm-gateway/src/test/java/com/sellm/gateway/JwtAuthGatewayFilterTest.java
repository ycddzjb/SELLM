package com.sellm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

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
}
