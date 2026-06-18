package com.sellm.scale;

import java.util.List;

public interface ScoringEngine {
    /**
     * 计分:校验 → 求和 → 匹配分段。
     * @throws ScoringException 规则缺失、作答不完整或无命中分段时
     */
    AssessmentResult score(Scale scale, List<Answer> answers);
}
