package com.sellm.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class RateLimitGatewayFilter implements GlobalFilter, Ordered {

    // 原子计数+设TTL:仅首次(c==1)设过期,避免 INCR/EXPIRE 两次往返非原子,
    // 也避免 limit=0 时 429 分支漏设 TTL 致键永不过期泄露。返回当前计数。
    private static final RedisScript<Long> INCR_TTL = RedisScript.of(
        "local c = redis.call('INCR', KEYS[1]) "
        + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
        + "return c", Long.class);

    private final ReactiveStringRedisTemplate redis;
    private final RateLimitProperties props;

    public RateLimitGatewayFilter(ReactiveStringRedisTemplate redis, RateLimitProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!props.isEnabled()) {
            return chain.filter(exchange);
        }
        String id = resolveKey(exchange);
        long window = System.currentTimeMillis() / 1000L / props.getWindowSeconds();
        String key = "ratelimit:" + id + ":" + window;

        return redis.execute(INCR_TTL, List.of(key), List.of(String.valueOf(props.getWindowSeconds())))
                .next()
                .onErrorReturn(-1L)        // fail-open:仅 Redis 操作异常时返哨兵放行(不含下游)
                .flatMap(count -> {
                    if (count >= 0 && count > props.getLimit()) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);   // 仅此一次转发下游,其错误自然传播不被吞
                });
    }

    private String resolveKey(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return "u:" + userId;
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return "ip:" + exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "ip:unknown";
    }

    @Override
    public int getOrder() {
        return 0; // 在 JwtAuthGatewayFilter(-100)之后,X-User-Id 已注入
    }
}
