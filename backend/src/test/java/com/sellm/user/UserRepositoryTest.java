package com.sellm.user;

import com.sellm.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 注册后能按用户名查到且密码已哈希() {
        jdbc.update("DELETE FROM app_user WHERE username = 't1'");
        AppUser saved = repository.register("t1", "secret123", Role.TEACHER, 1L);
        assertThat(saved.getId()).isNotNull();

        AppUser found = repository.findByUsername("t1");
        assertThat(found).isNotNull();
        assertThat(found.getRole()).isEqualTo(Role.TEACHER);
        // 库里存的是 BCrypt 哈希,不是明文
        assertThat(found.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(found.getPasswordHash()).startsWith("$2");
        // 旧 4 参 register 默认 status=ACTIVE
        assertThat(found.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void 五参注册能存PENDING并读回() {
        jdbc.update("DELETE FROM app_user WHERE username = 't3'");
        repository.register("t3", "pw123456", Role.PARENT, 2L, "PENDING");
        AppUser found = repository.findByUsername("t3");
        assertThat(found).isNotNull();
        assertThat(found.getRole()).isEqualTo(Role.PARENT);
        assertThat(found.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void 密码校验正确与错误() {
        jdbc.update("DELETE FROM app_user WHERE username = 't2'");
        repository.register("t2", "rightpass", Role.MANAGER, 1L);
        AppUser u = repository.findByUsername("t2");
        assertThat(repository.matches("rightpass", u.getPasswordHash())).isTrue();
        assertThat(repository.matches("wrongpass", u.getPasswordHash())).isFalse();
    }

    @Test
    void 用户名不存在返回null() {
        assertThat(repository.findByUsername("nobody-xyz")).isNull();
    }
}
