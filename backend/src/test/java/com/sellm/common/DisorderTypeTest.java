package com.sellm.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DisorderTypeTest {

    @Test
    void 八个品类齐全且有中文标签() {
        assertThat(DisorderType.values()).hasSize(8);
        assertThat(DisorderType.ASD.getLabel()).isEqualTo("孤独症");
        assertThat(DisorderType.ADHD.getLabel()).isEqualTo("注意缺陷多动障碍");
    }

    @Test
    void 合法逗号串校验通过() {
        DisorderType.validateCsv("ASD,ADHD");
        DisorderType.validateCsv("");
        DisorderType.validateCsv(null);
    }

    @Test
    void 非法码校验抛异常() {
        assertThatThrownBy(() -> DisorderType.validateCsv("ASD,NOPE"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 逗号串转列表() {
        assertThat(DisorderType.fromCsv("ASD,ADHD")).containsExactly(DisorderType.ASD, DisorderType.ADHD);
        assertThat(DisorderType.fromCsv(" ASD , LANGUAGE ")).containsExactly(DisorderType.ASD, DisorderType.LANGUAGE);
        assertThat(DisorderType.fromCsv("")).isEmpty();
    }
}
