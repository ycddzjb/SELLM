package com.sellm.report;

import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.PromptRequest;
import com.sellm.rag.KnowledgeDoc;
import com.sellm.rag.RagRetriever;
import com.sellm.scale.AssessmentResult;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ReportService {

    private final RagRetriever ragRetriever;
    private final AiGateway aiGateway;

    public ReportService(RagRetriever ragRetriever, AiGateway aiGateway) {
        this.ragRetriever = ragRetriever;
        this.aiGateway = aiGateway;
    }

    public Report generateDraft(String childName, String schoolName,
                                String scaleName, AssessmentResult result) {
        List<KnowledgeDoc> docs = ragRetriever.retrieve(
            scaleName + " " + result.getBandLabel() + " 解读", 3);

        StringBuilder knowledge = new StringBuilder();
        for (KnowledgeDoc doc : docs) {
            knowledge.append(doc.getContent()).append("\n");
        }

        String prompt = "请基于以下信息为 " + childName + "(" + schoolName + ")生成评估报告草稿。\n"
            + "量表: " + scaleName + "\n"
            + "总分: " + result.getTotalScore() + ",分段: " + result.getBandLabel() + "\n"
            + "解读参考: " + result.getInterpretation() + "\n"
            + "知识库召回:\n" + knowledge;

        String draft = aiGateway.generate(
            new PromptRequest(prompt, List.of(childName), List.of(schoolName)));

        return new Report(childName, draft);
    }
}
