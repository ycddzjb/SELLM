package com.sellm.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthGatewayFilter implements GlobalFilter, Ordered {

    private final GatewayJwtProperties props;

    public JwtAuthGatewayFilter(GatewayJwtProperties props) {
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 规范化路径，防止 /api/auth/../teaching/x 形式的路径穿越绕过白名单
        String path = java.net.URI.create(exchange.getRequest().getURI().getRawPath()).normalize().getPath();
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        String token = auth.substring(7);

        try {
            SecretKey key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();

            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-User-Id", claims.get("uid") == null ? "" : String.valueOf(claims.get("uid")))
                    .header("X-User-Name", claims.getSubject() == null ? "" : claims.getSubject())
                    .header("X-User-Role", claims.get("role") == null ? "" : String.valueOf(claims.get("role")))
                    .header("X-Org-Id", claims.get("org") == null ? "" : String.valueOf(claims.get("org")))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            return unauthorized(exchange);
        }
    }

    private boolean isWhitelisted(String path) {
        return props.getWhitelist().stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100; // 在路由转发前执行
    }
}
