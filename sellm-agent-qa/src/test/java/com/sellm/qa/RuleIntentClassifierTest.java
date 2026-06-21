package com.sellm.qa;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuleIntentClassifierTest {

    private final IntentClassifier c = new RuleIntentClassifier();

    @Test
    void 评估类问题归ASSESSMENT() {
        assertEquals(Intent.ASSESSMENT, c.classify("帮我用CARS量表评估这个孩子"));
        assertEquals(Intent.ASSESSMENT, c.classify("怎么做评估测评"));
    }

    @Test
    void 备课类归TEACHING() {
        assertEquals(Intent.TEACHING, c.classify("帮我备课写个教案"));
    }

    @Test
    void 教具类归AIDS() {
        assertEquals(Intent.AIDS, c.classify("推荐适合自闭症的教具"));
    }

    @Test
    void 科研类归RESEARCH() {
        assertEquals(Intent.RESEARCH, c.classify("帮我算量表的信效度 Cronbach"));
    }

    @Test
    void 通用问题兜底GENERAL() {
        assertEquals(Intent.GENERAL, c.classify("孤独症儿童的融合教育政策有哪些"));
        assertEquals(Intent.GENERAL, c.classify("什么是特殊教育"));
    }

    @Test
    void Intent携带路由与深链() {
        assertEquals("assessment", Intent.ASSESSMENT.getRouteTo());
        assertEquals("/assessment", Intent.ASSESSMENT.getDeepLink());
        assertNull(Intent.GENERAL.getRouteTo());
        assertNull(Intent.GENERAL.getDeepLink());
    }
}
