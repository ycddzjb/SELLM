package com.sellm.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private final JwtService jwt =
        new JwtService("test-jwt-secret-key-at-least-32-bytes-long-0123456789", 120);

    @Test
    void 签发的token能解析出用户名角色uid与org() {
        String token = jwt.issue("t1", "TEACHER", 7L, 3L);
        assertThat(jwt.extractUsername(token)).isEqualTo("t1");
        assertThat(jwt.extractRole(token)).isEqualTo("TEACHER");
        assertThat(jwt.extractUserId(token)).isEqualTo(7L);
        assertThat(jwt.extractOrgId(token)).isEqualTo(3L);
    }

    @Test
    void orgId可为null() {
        String token = jwt.issue("t1", "MANAGER", 1L, null);
        assertThat(jwt.extractOrgId(token)).isNull();
    }

    @Test
    void 合法token校验通过() {
        String token = jwt.issue("t1", "TEACHER", 7L, 3L);
        assertThat(jwt.isValid(token)).isTrue();
    }

    @Test
    void 篡改的token校验失败() {
        String token = jwt.issue("t1", "TEACHER", 7L, 3L);
        // Replace the payload with different data but keep the structure
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + ".eyJzdWIiOiJ0MiJ9." + parts[2];
        assertThat(jwt.isValid(tamperedToken)).isFalse();
    }

    @Test
    void 无效签名的token校验失败() {
        String token = jwt.issue("t1", "TEACHER", 7L, 3L);
        // 修改token的签名部分
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + ".invalidsignature";
        assertThat(jwt.isValid(tamperedToken)).isFalse();
    }
}


