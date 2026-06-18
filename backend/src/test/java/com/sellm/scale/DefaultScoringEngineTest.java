package com.sellm.scale;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DefaultScoringEngineTest {

    private final ScoringEngine engine = new DefaultScoringEngine();

    private Scale scaleWithRule() {
        List<ScaleItem> items = List.of(
            new ScaleItem("q1", "社交", "社交"),
            new ScaleItem("q2", "沟通", "沟通")
        );
        ScoringRule rule = new ScoringRule(List.of(
            new ScoreBand(0, 3, "正常", "未见明显异常"),
            new ScoreBand(4, 7, "轻-中度", "建议进一步评估")
        ));
        return new Scale("cars", "CARS", "v1", items, rule);
    }

    @Test
    void 求和并命中正确分段() {
        AssessmentResult r = engine.score(scaleWithRule(),
            List.of(new Answer("q1", 2), new Answer("q2", 3)));
        assertThat(r.getTotalScore()).isEqualTo(5.0);
        assertThat(r.getBandLabel()).isEqualTo("轻-中度");
        assertThat(r.getInterpretation()).isEqualTo("建议进一步评估");
    }

    @Test
    void 边界值落在分段上界() {
        AssessmentResult r = engine.score(scaleWithRule(),
            List.of(new Answer("q1", 1), new Answer("q2", 2)));
        assertThat(r.getTotalScore()).isEqualTo(3.0);
        assertThat(r.getBandLabel()).isEqualTo("正常");
    }

    @Test
    void 作答不完整则抛异常() {
        assertThatThrownBy(() ->
            engine.score(scaleWithRule(), List.of(new Answer("q1", 2)))
        ).isInstanceOf(ScoringException.class)
         .hasMessageContaining("作答不完整");
    }

    @Test
    void 计分规则缺失则抛异常() {
        Scale noRule = new Scale("x", "X", "v1",
            List.of(new ScaleItem("q1", "题", "维度")), null);
        assertThatThrownBy(() ->
            engine.score(noRule, List.of(new Answer("q1", 1)))
        ).isInstanceOf(ScoringException.class)
         .hasMessageContaining("计分规则缺失");
    }

    @Test
    void 总分无命中分段则抛异常() {
        assertThatThrownBy(() ->
            engine.score(scaleWithRule(),
                List.of(new Answer("q1", 50), new Answer("q2", 50)))
        ).isInstanceOf(ScoringException.class)
         .hasMessageContaining("无命中分段");
    }
}
