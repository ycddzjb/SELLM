package com.sellm.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitGatewayFilterTest {

    private RateLimitProperties props() {
        RateLimitProperties p = new RateLimitProperties();
        p.setEnabled(true);
        p.setLimit(2);
        p.setWindowSeconds(60);
        return p;
    }

    @SuppressWarnings("unchecked")
    private ReactiveStringRedisTemplate redisReturning(Long count) {
        ReactiveStringRedisTemplate tpl = mock(ReactiveStringRedisTemplate.class);
        // 新实现用 execute(Lua 脚本)原子 INCR+EXPIRE,返回当前计数
        when(tpl.execute(any(RedisScript.class), anyList(), anyList()))
            .thenReturn(Flux.just(count));
        return tpl;
    }

    @Test
    void 未超限放行() {
        RateLimitGatewayFilter filter = new RateLimitGatewayFilter(redisReturning(1L), props());
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/teaching/x").header("X-User-Id", "42").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        final boolean[] passed = {false};
        GatewayFilterChain chain = e -> { passed[0] = true; return Mono.empty(); };
        filter.filter(exchange, chain).block();
        assertTrue(passed[0]);
        assertNotEquals(429, exchange.getResponse().getStatusCode() == null ? 0 : exchange.getResponse().getStatusCode().value());
    }

    @Test
    void 超限返回429() {
        RateLimitGatewayFilter filter = new RateLimitGatewayFilter(redisReturning(3L), props());
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/teaching/x").header("X-User-Id", "42").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        GatewayFilterChain chain = e -> Mono.empty();
        filter.filter(exchange, chain).block();
        assertEquals(429, exchange.getResponse().getStatusCode().value());
    }

    @SuppressWarnings("unchecked")
    @Test
    void redis异常放行failopen() {
        ReactiveStringRedisTemplate tpl = mock(ReactiveStringRedisTemplate.class);
        when(tpl.execute(any(RedisScript.class), anyList(), anyList()))
            .thenReturn(Flux.error(new RuntimeException("redis down")));
        RateLimitGatewayFilter filter = new RateLimitGatewayFilter(tpl, props());
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/teaching/x").header("X-User-Id", "42").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);
        final boolean[] passed = {false};
        GatewayFilterChain chain = e -> { passed[0] = true; return reactor.core.publisher.Mono.empty(); };
        filter.filter(exchange, chain).block();
        assertTrue(passed[0], "Redis 异常应 fail-open 放行");
    }
}
