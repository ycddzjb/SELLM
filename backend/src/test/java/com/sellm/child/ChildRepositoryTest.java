package com.sellm.child;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ChildRepositoryTest {

    @Autowired
    private ChildRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 保存后能按id读出明文姓名() {
        Child saved = repository.save(new Child(null, "小明", "ASD", 1L));
        assertThat(saved.getId()).isNotNull();

        Child loaded = repository.findById(saved.getId());
        assertThat(loaded.getName()).isEqualTo("小明");
        assertThat(loaded.getDisorderType()).isEqualTo("ASD");
        assertThat(loaded.getOrgId()).isEqualTo(1L);
    }

    @Test
    void 库中存储的是密文而非明文() {
        Child saved = repository.save(new Child(null, "张伟", "ASD", 1L));
        String stored = jdbc.queryForObject(
            "SELECT name_enc FROM child WHERE id = ?", String.class, saved.getId());
        assertThat(stored).isNotEqualTo("张伟");
        assertThat(stored).doesNotContain("张伟");
    }

    @Test
    void 查不到返回null() {
        assertThat(repository.findById(999999L)).isNull();
    }
}
