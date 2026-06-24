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
    private final SecretKey key;

    public JwtAuthGatewayFilter(GatewayJwtProperties props) {
        this.props = props;
        // fail-fast:密钥缺失或不足 32 字节(HS256 要求)即拒绝启动,防止漏配 SELLM_JWT_SECRET
        // 时用公开默认密钥验签导致认证绕过(参照 sellm-common-core JwtService 的校验)。
        String secret = props.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "sellm.gateway.jwt.secret 必须至少 32 字节(请经 SELLM_JWT_SECRET 注入,生产勿用开发默认值)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Fix(defense-in-depth): 在入口无条件剥离客户端可能伪造的用户上下文头，
        // 防止白名单路径上的 header 注入攻击。认证通过后下方代码会从 JWT claims 重新写入。
        ServerWebExchange cleaned = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(h -> {
                            h.remove("X-User-Id");
                            h.remove("X-User-Name");
                            h.remove("X-User-Role");
                            h.remove("X-Org-Id");
                        })
                        .build())
                .build();

        // Fix(path-traversal): 先解码原始路径中的百分比编码（包括 %2e%2e），再做 normalize，
        // 防止 /api/auth/%2e%2e/teaching/x 绕过白名单（getPath().value() 返回 raw 路径，
        // URI.normalize() 不处理编码点；必须先解码再规范化）。
        String rawPath = cleaned.getRequest().getURI().getRawPath();
        String path;
        try {
            // URI(scheme,host,path,fragment) 构造器接受已解码的 path；getRawPath 的百分比序列
            // 经 URI.create("http://h" + rawPath).getPath() 还原为 Unicode，再重建 URI 执行 normalize。
            String decodedPath = java.net.URI.create("http://h" + rawPath).getPath();
            path = new java.net.URI(null, null, decodedPath, null).normalize().getPath();
        } catch (Exception ex) {
            // 路径格式异常时拒绝
            return unauthorized(cleaned);
        }
        if (isWhitelisted(path)) {
            return chain.filter(cleaned);
        }

        String auth = cleaned.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(cleaned);
        }
        String token = auth.substring(7);

        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();

            ServerHttpRequest mutated = cleaned.getRequest().mutate()
                    .header("X-User-Id", claims.get("uid") == null ? "" : String.valueOf(claims.get("uid")))
                    .header("X-User-Name", claims.getSubject() == null ? "" : claims.getSubject())
                    .header("X-User-Role", claims.get("role") == null ? "" : String.valueOf(claims.get("role")))
                    .header("X-Org-Id", claims.get("org") == null ? "" : String.valueOf(claims.get("org")))
                    .build();
            return chain.filter(cleaned.mutate().request(mutated).build());
        } catch (Exception e) {
            return unauthorized(cleaned);
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
