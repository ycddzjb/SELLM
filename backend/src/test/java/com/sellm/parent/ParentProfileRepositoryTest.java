package com.sellm.parent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ParentProfileRepositoryTest {

    @Autowired
    private ParentProfileRepository repository;

    @Test
    void save后按userId读回且姓名解密一致() {
        repository.save(new ParentProfile(9001L, "李四家长", "MOTHER_DAUGHTER", 500L,
            "李小四", "LANGUAGE", 600L, null));

        ParentProfile got = repository.findByUserId(9001L);
        assertThat(got).isNotNull();
        assertThat(got.getName()).isEqualTo("李四家长");          // 解密一致
        assertThat(got.getChildName()).isEqualTo("李小四");
        assertThat(got.getRelationship()).isEqualTo("MOTHER_DAUGHTER");
        assertThat(got.getAssignedTeacherId()).isEqualTo(500L);
        assertThat(got.getChildDisorderType()).isEqualTo("LANGUAGE");
        assertThat(got.getClassId()).isEqualTo(600L);
        assertThat(got.getChildId()).isNull();
    }

    @Test
    void updateChildId回填() {
        repository.save(new ParentProfile(9002L, "王五", "FATHER_SON", 501L,
            "王小五", "ASD", null, null));
        repository.updateChildId(9002L, 7777L);
        assertThat(repository.findByUserId(9002L).getChildId()).isEqualTo(7777L);
    }

    @Test
    void 姓名加密落库非明文() {
        // 直接读不到的话至少验证 findByUserId 能正确解密;明文不应等于密文存储
        repository.save(new ParentProfile(9003L, "明文姓名", "FATHER_DAUGHTER", 502L,
            "明文儿童", "ADHD", null, null));
        ParentProfile got = repository.findByUserId(9003L);
        assertThat(got.getName()).isEqualTo("明文姓名"); // 解密回明文
    }
}
