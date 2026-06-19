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

    @Test
    void 保存新字段后能读出() {
        Organization saved = repository.save(new Organization(null, "康复中心", "江苏", "ASD,ADHD", "江苏", "南京"));
        assertThat(saved.getId()).isNotNull();
        Organization found = repository.findById(saved.getId());
        assertThat(found.getName()).isEqualTo("康复中心");
        assertThat(found.getRegion()).isEqualTo("江苏");
        assertThat(found.getDisorderTypes()).isEqualTo("ASD,ADHD");
        assertThat(found.getProvince()).isEqualTo("江苏");
        assertThat(found.getCity()).isEqualTo("南京");
    }

    @Test
    void listAll包含新字段() {
        repository.save(new Organization(null, "A学校", "北京", "ASD", "北京", "朝阳"));
        var list = repository.listAll();
        assertThat(list).isNotEmpty();
        Organization org = list.get(list.size() - 1);
        assertThat(org.getDisorderTypes()).isEqualTo("ASD");
        assertThat(org.getProvince()).isEqualTo("北京");
        assertThat(org.getCity()).isEqualTo("朝阳");
    }
}

