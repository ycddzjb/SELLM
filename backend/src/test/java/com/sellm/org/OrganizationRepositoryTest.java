package com.sellm.org;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OrganizationRepositoryTest {

    @Autowired
    private OrganizationRepository repository;

    @Test
    void 保存后能按id取出机构名() {
        Organization saved = repository.save(new Organization(null, "阳光小学", "南京"));
        assertThat(saved.getId()).isNotNull();
        assertThat(repository.nameOf(saved.getId())).isEqualTo("阳光小学");
    }

    @Test
    void 机构不存在返回兜底名() {
        assertThat(repository.nameOf(999999L)).isEqualTo("未知机构");
        assertThat(repository.nameOf(null)).isEqualTo("未知机构");
    }
}
