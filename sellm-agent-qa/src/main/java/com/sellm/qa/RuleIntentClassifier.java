package com.sellm.qa;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/** 关键词规则意图分类器(默认实现)。优先级自上而下,首个命中即返回;都不命中 → GENERAL 兜底。 */
@Component
public class RuleIntentClassifier implements IntentClassifier {

    // 顺序敏感:靠前的优先匹配
    private static final List<Map.Entry<Intent, List<String>>> RULES = List.of(
        Map.entry(Intent.ASSESSMENT, List.of("评估", "测评", "评分", "cars", "abc量表")),
        Map.entry(Intent.TEACHING, List.of("备课", "教案", "课件", "教学设计", "分层教学")),
        Map.entry(Intent.AIDS, List.of("教具", "绘本", "文生图", "ar", "vr", "资源库")),
        Map.entry(Intent.RESEARCH, List.of("文献", "信效度", "课题", "论文", "cronbach"))
    );

    @Override
    public Intent classify(String question) {
        if (question == null) return Intent.GENERAL;
        String q = question.toLowerCase();
        for (Map.Entry<Intent, List<String>> rule : RULES) {
            for (String kw : rule.getValue()) {
                if (q.contains(kw)) return rule.getKey();
            }
        }
        return Intent.GENERAL;
    }
}
