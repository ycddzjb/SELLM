package com.sellm.iep;

import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.PromptRequest;
import com.sellm.rag.KnowledgeDoc;
import com.sellm.rag.RagRetriever;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class IepService {

    private final RagRetriever ragRetriever;
    private final AiGateway aiGateway;

    public IepService(RagRetriever ragRetriever, AiGateway aiGateway) {
        this.ragRetriever = ragRetriever;
        this.aiGateway = aiGateway;
    }

    public Iep generateDraft(String childName, String schoolName, String assessmentConclusion) {
        return generateDraft(childName, schoolName, assessmentConclusion, null);
    }

    public Iep generateDraft(String childName, String schoolName, String assessmentConclusion,
                             String profileContext) {
        List<KnowledgeDoc> docs = ragRetriever.retrieve(
            "ASD IEP 干预 " + assessmentConclusion, 3);

        StringBuilder knowledge = new StringBuilder();
        for (KnowledgeDoc doc : docs) {
            knowledge.append(doc.getContent()).append("\n");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是特殊教育 IEP 规划专家。请为 ").append(childName)
            .append("(").append(schoolName).append(")生成 IEP 草案,")
            .append("包含【长期目标】【短期目标】【干预活动建议】三部分,供教师编辑定稿。\n");
        prompt.append("评估结论: ").append(assessmentConclusion).append("\n");
        if (profileContext != null && !profileContext.isBlank()) {
            prompt.append("既往档案:\n").append(profileContext).append("\n");
        }
        prompt.append("可参考的范例与策略:\n").append(knowledge);

        String draft = aiGateway.generate(
            new PromptRequest(prompt.toString(), List.of(childName), List.of(schoolName)));

        return new Iep(childName, draft);
    }
}
