package com.sellm.iep;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IepTest {

    @Test
    void 初始状态为DRAFT() {
        Iep iep = new Iep("小明", "草案");
        assertThat(iep.getStatus()).isEqualTo(IepStatus.DRAFT);
        assertThat(iep.getFinalizedContent()).isNull();
    }

    @Test
    void 定稿保存内容并置为FINALIZED() {
        Iep iep = new Iep("小明", "草案");
        iep.finalizePlan("老师修改后的IEP终稿");
        assertThat(iep.getStatus()).isEqualTo(IepStatus.FINALIZED);
        assertThat(iep.getFinalizedContent()).isEqualTo("老师修改后的IEP终稿");
    }
}
