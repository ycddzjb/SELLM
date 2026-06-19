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
                // 其余 /api/** 需登录(GET 三角色都可,行级权限在 service 层用 AccessGuard 控制)
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
