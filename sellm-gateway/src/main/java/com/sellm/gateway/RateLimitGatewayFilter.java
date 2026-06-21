package com.sellm.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class RateLimitGatewayFilter implements GlobalFilter, Ordered {

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

        return redis.opsForValue().increment(key)
                .flatMap(count -> {
                    Mono<Void> proceed = redis.expire(key, Duration.ofSeconds(props.getWindowSeconds()))
                            .then(Mono.defer(() -> chain.filter(exchange)));
                    if (count != null && count > props.getLimit()) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                    return proceed;
                })
                .onErrorResume(e -> chain.filter(exchange)); // fail-open:Redis 异常不阻断
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
