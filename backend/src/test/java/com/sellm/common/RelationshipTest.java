package com.sellm.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RelationshipTest {

    @Test
    void 四种关系齐全且有中文标签() {
        assertThat(Relationship.values()).hasSize(4);
        assertThat(Relationship.MOTHER_SON.getLabel()).isEqualTo("母子");
        assertThat(Relationship.FATHER_DAUGHTER.getLabel()).isEqualTo("父女");
    }

    @Test
    void 合法关系码校验通过() {
        Relationship.validate("MOTHER_SON");
        Relationship.validate("");
        Relationship.validate(null);
    }

    @Test
    void 非法关系码校验抛异常() {
        assertThatThrownBy(() -> Relationship.validate("UNCLE"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void labelOf兜底() {
        assertThat(Relationship.labelOf("FATHER_SON")).isEqualTo("父子");
        assertThat(Relationship.labelOf("")).isEqualTo("");
        assertThat(Relationship.labelOf("UNKNOWN")).isEqualTo("UNKNOWN");
    }
}
