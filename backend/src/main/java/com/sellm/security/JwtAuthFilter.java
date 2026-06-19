package com.sellm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isValid(token)) {
                try {
                    String username = jwtService.extractUsername(token);
                    String roleStr = jwtService.extractRole(token);
                    Long uid = jwtService.extractUserId(token);
                    Long org = jwtService.extractOrgId(token);
                    if (username != null && roleStr != null) {
                        AuthPrincipal principal = new AuthPrincipal(uid, username, Role.valueOf(roleStr), org);
                        var auth = new UsernamePasswordAuthenticationToken(
                            principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + roleStr)));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } catch (IllegalArgumentException e) {
                    // role 非法(枚举值不存在) → 不设置认证,交授权层按未认证处理,避免 500
                }
            }
        }
        chain.doFilter(request, response);
    }
}
