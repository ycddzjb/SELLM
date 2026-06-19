package com.sellm.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                // 写评估/报告/IEP:仅 TEACHER、MANAGER
                .requestMatchers(HttpMethod.POST, "/api/assessments/**", "/api/reports/**", "/api/ieps/**")
                    .hasAnyRole("TEACHER", "MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/assessments/**", "/api/reports/**", "/api/ieps/**")
                    .hasAnyRole("TEACHER", "MANAGER")
                // 写 child:TEACHER、MANAGER
                .requestMatchers(HttpMethod.POST, "/api/children/**").hasAnyRole("TEACHER", "MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/children/**").hasAnyRole("TEACHER", "MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/api/children/**").hasAnyRole("TEACHER", "MANAGER")
                // 建账号:超管(建 MANAGER)+ 机构管理者(建 TEACHER/PARENT);具体角色能建什么由 controller 细分
                .requestMatchers(HttpMethod.POST, "/api/users/**").hasAnyRole("SUPER_ADMIN", "MANAGER")
                // 全角色改自己密码(任何登录用户);放在 /api/users/* 通配之前以正确命中
                .requestMatchers(HttpMethod.PUT, "/api/users/me/password").authenticated()
                // 机构管理者:看本机构待审家长 + 审核(通过/拒绝)
                .requestMatchers(HttpMethod.GET, "/api/users/pending").hasRole("MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/users/*/approve", "/api/users/*/reject").hasRole("MANAGER")
                // 机构端点:公开列表免登录(注册选机构),建机构/看全部限超管
                .requestMatchers(HttpMethod.GET, "/api/orgs/public").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orgs").hasRole("SUPER_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/orgs").hasRole("SUPER_ADMIN")
                // 其余 /api/** 需登录(GET 三角色都可,行级权限在 service 层用 AccessGuard 控制)
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
