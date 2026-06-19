package com.sellm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${sellm.jwt.secret:}") String secret,
                      @Value("${sellm.jwt.expiration-minutes:120}") long expirationMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("sellm.jwt.secret 必须至少 32 字节(HS256)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String issue(String username, String role, Long userId, Long orgId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMinutes * 60_000);
        return issueWithDates(username, role, userId, orgId, now, exp);
    }

    protected String issueWithDates(String username, String role, Long userId, Long orgId, Date now, Date exp) {
        return Jwts.builder()
            .subject(username)
            .claim("role", role)
            .claim("uid", userId)
            .claim("org", orgId)
            .issuedAt(now)
            .expiration(exp)
            .signWith(key)
            .compact();
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public String extractRole(String token) {
        return parse(token).get("role", String.class);
    }

    public Long extractUserId(String token) {
        Number n = parse(token).get("uid", Number.class);
        return n == null ? null : n.longValue();
    }

    public Long extractOrgId(String token) {
        Number n = parse(token).get("org", Number.class);
        return n == null ? null : n.longValue();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}



