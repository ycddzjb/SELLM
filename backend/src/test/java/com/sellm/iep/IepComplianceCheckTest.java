package com.sellm.iep;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 合规 red-flag 兜底校验单元测试(不依赖 Spring)。 */
class IepComplianceCheckTest {

    private final IepService service = new IepService(null, null);

    @Test
    void 命中红线词前置警示() {
        String draft = "训练步骤:对不配合的儿童采用体罚以纠正行为。";
        String out = service.applyComplianceCheck(draft);
        assertThat(out).startsWith("⚠️ 合规提示");
        assertThat(out).contains("体罚");
        assertThat(out).contains(draft);   // 原文保留供教师复核
    }

    @Test
    void 多种红线词均能检出() {
        for (String flag : new String[]{"厌恶疗法", "电击", "束缚", "禁食", "打骂"}) {
            assertThat(service.applyComplianceCheck("方案含" + flag + "内容"))
                .startsWith("⚠️ 合规提示");
        }
    }

    @Test
    void 正常草案不加警示() {
        String draft = "动作训练:剥珠分步法,每日2次;社交互动:结构化游戏增加眼神接触。";
        assertThat(service.applyComplianceCheck(draft)).isEqualTo(draft);
    }

    @Test
    void null安全() {
        assertThat(service.applyComplianceCheck(null)).isNull();
    }
}
