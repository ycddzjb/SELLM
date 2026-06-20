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

    @Test
    void 扩展字段保存后读回一致() {
        Child c = new Child(null, "小华", "ADHD", 1L);
        c.setBaselineSummary("基线:社交弱");
        c.setAnnualIepSummary("年度:提升表达");
        c.setMonthlyGoal("月度:每日跟读");
        c.setReassessDate("2026-09-01");
        c.setIepDueDate("2026-12-31");
        c.setInterventionProgress("进行中");
        Child saved = repository.save(c);

        Child loaded = repository.findById(saved.getId());
        assertThat(loaded.getBaselineSummary()).isEqualTo("基线:社交弱");
        assertThat(loaded.getAnnualIepSummary()).isEqualTo("年度:提升表达");
        assertThat(loaded.getMonthlyGoal()).isEqualTo("月度:每日跟读");
        assertThat(loaded.getReassessDate()).isEqualTo("2026-09-01");
        assertThat(loaded.getIepDueDate()).isEqualTo("2026-12-31");
        assertThat(loaded.getInterventionProgress()).isEqualTo("进行中");
    }

    @Test
    void update可改扩展字段() {
        Child saved = repository.save(new Child(null, "小强", "ASD", 1L));
        saved.setMonthlyGoal("改后目标");
        saved.setReassessDate("2027-01-15");
        repository.update(saved);

        Child loaded = repository.findById(saved.getId());
        assertThat(loaded.getMonthlyGoal()).isEqualTo("改后目标");
        assertThat(loaded.getReassessDate()).isEqualTo("2027-01-15");
    }
}
