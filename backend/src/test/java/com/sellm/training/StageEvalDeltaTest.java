package com.sellm.training;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 阶段评估量化 delta 计算单元测试(纯 Java,不依赖 Spring/AI)。 */
class StageEvalDeltaTest {

    private final StageEvalService service = new StageEvalService(null, null);

    @Test
    void 汇总同指标多条记录取均值() {
        String s1 = "[{\"item\":\"bead\",\"score\":2,\"maxScore\":4}]";
        String s2 = "[{\"item\":\"bead\",\"score\":4,\"maxScore\":4}]";
        String summary = service.summarizeScores(java.util.List.of(s1, s2));
        // bead 均值 (2+4)/2 = 3
        assertThat(summary).contains("bead").contains("3");
    }

    @Test
    void delta计算进步与达标() {
        String prev = service.summarizeScores(java.util.List.of("[{\"item\":\"bead\",\"score\":2,\"maxScore\":4}]"));
        String cur = service.summarizeScores(java.util.List.of("[{\"item\":\"bead\",\"score\":4,\"maxScore\":4}]"));
        String delta = service.computeDelta(cur, prev);
        // 本期 4/4 达标(≥80%),delta = 4-2 = 2 进步
        assertThat(delta).contains("\"delta\":2");
        assertThat(delta).contains("\"reached\":true");
        assertThat(delta).contains("\"improvedItems\":1");
        assertThat(delta).contains("\"metItems\":1");
    }

    @Test
    void 首期无上期delta为null不报错() {
        String cur = service.summarizeScores(java.util.List.of("[{\"item\":\"bead\",\"score\":1,\"maxScore\":4}]"));
        String delta = service.computeDelta(cur, null);
        assertThat(delta).contains("\"previous\":null");
        assertThat(delta).contains("\"reached\":false");  // 1/4 未达标
        assertThat(delta).contains("\"improvedItems\":0");
    }

    @Test
    void 空得分汇总不报错() {
        assertThat(service.summarizeScores(java.util.List.of())).isEqualTo("{}");
        assertThat(service.computeDelta("{}", null)).contains("\"totalItems\":0");
    }
}
