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
        List<KnowledgeDoc> docs = ragRetriever.retrieve(
            "ASD IEP 干预 " + assessmentConclusion, 3);

        StringBuilder knowledge = new StringBuilder();
        for (KnowledgeDoc doc : docs) {
            knowledge.append(doc.getContent()).append("\n");
        }

        String prompt = "请为 " + childName + "(" + schoolName + ")生成 IEP 草案,"
            + "包含长短期目标与干预活动建议。\n"
            + "评估结论: " + assessmentConclusion + "\n"
            + "可参考的范例与策略:\n" + knowledge;

        String draft = aiGateway.generate(
            new PromptRequest(prompt, List.of(childName), List.of(schoolName)));

        return new Iep(childName, draft);
    }
}
