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
        return generateDraft(childName, schoolName, scaleName, result, null);
    }

    public Report generateDraft(String childName, String schoolName,
                                String scaleName, AssessmentResult result, String profileContext) {
        List<KnowledgeDoc> docs = ragRetriever.retrieve(
            scaleName + " " + result.getBandLabel() + " 解读", 3);

        StringBuilder knowledge = new StringBuilder();
        for (KnowledgeDoc doc : docs) {
            knowledge.append(doc.getContent()).append("\n");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是特殊教育评估专家。请基于以下信息为 ").append(childName)
            .append("(").append(schoolName).append(")生成一份结构化的评估报告草案,")
            .append("分为【基本情况】【评估结果】【优势与困难】【教育建议】四节,供教师编辑定稿。\n");
        prompt.append("量表: ").append(scaleName).append("\n");
        prompt.append("总分: ").append(result.getTotalScore())
            .append(",分段: ").append(result.getBandLabel()).append("\n");
        prompt.append("解读参考: ").append(result.getInterpretation()).append("\n");
        if (profileContext != null && !profileContext.isBlank()) {
            prompt.append("既往档案:\n").append(profileContext).append("\n");
        }
        prompt.append("知识库召回:\n").append(knowledge);

        String draft = aiGateway.generate(
            new PromptRequest(prompt.toString(), List.of(childName), List.of(schoolName)));

        return new Report(childName, draft);
    }
}
