package com.sellm.scale;

import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class DefaultScoringEngine implements ScoringEngine {

    @Override
    public AssessmentResult score(Scale scale, List<Answer> answers) {
        if (scale.getScoringRule() == null) {
            throw new ScoringException("计分规则缺失: 量表 " + scale.getName());
        }
        Set<String> answered = new HashSet<>();
        for (Answer a : answers) {
            answered.add(a.getItemId());
        }
        for (ScaleItem item : scale.getItems()) {
            if (!answered.contains(item.getItemId())) {
                throw new ScoringException("作答不完整: 缺少题目 " + item.getItemId());
            }
        }

        double total = 0;
        for (Answer a : answers) {
            total += a.getScore();
        }

        for (ScoreBand band : scale.getScoringRule().getBands()) {
            if (band.contains(total)) {
                return new AssessmentResult(total, band.getLabel(), band.getInterpretation());
            }
        }
        throw new ScoringException("无命中分段: 总分 " + total + " 超出规则范围");
    }
}
