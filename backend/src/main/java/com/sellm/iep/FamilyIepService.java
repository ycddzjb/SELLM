package com.sellm.iep;

import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.PromptRequest;
import com.sellm.rag.KnowledgeDoc;
import com.sellm.rag.RagRetriever;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FamilyIepService {

    private final RagRetriever ragRetriever;
    private final AiGateway aiGateway;

    public FamilyIepService(RagRetriever ragRetriever, AiGateway aiGateway) {
        this.ragRetriever = ragRetriever;
        this.aiGateway = aiGateway;
    }

    /** 按家长目标 + 最新定稿评估结论,生成家庭训练 IEP 草案(标准模板)。 */
    public String generateDraft(String childName, String schoolName,
                                String parentGoal, String latestReportConclusion) {
        List<KnowledgeDoc> docs = ragRetriever.retrieve(
            "家庭 训练 IEP " + parentGoal, 3);
        StringBuilder knowledge = new StringBuilder();
        for (KnowledgeDoc doc : docs) {
            knowledge.append(doc.getContent()).append("\n");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是特殊教育家庭训练指导专家。请为 ").append(childName)
            .append("(").append(schoolName).append(")生成一份家庭训练 IEP 计划草案,")
            .append("按【家庭训练目标】【每周训练活动】【家长操作要点】【注意事项】分节,")
            .append("内容需贴合家庭场景、家长可独立执行。\n");
        prompt.append("家长设定的目标: ").append(parentGoal).append("\n");
        prompt.append("最新评估报告结论:\n").append(latestReportConclusion).append("\n");
        prompt.append("可参考的家庭训练策略:\n").append(knowledge);

        return aiGateway.generate(
            new PromptRequest(prompt.toString(), List.of(childName), List.of(schoolName)));
    }
}
