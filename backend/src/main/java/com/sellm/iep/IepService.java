package com.sellm.iep;

import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.PromptRequest;
import com.sellm.rag.KnowledgeDoc;
import com.sellm.rag.RagRetriever;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class IepService {

    /** 合规红线关键词:命中则在草案前置警示(prompt 约束为主,此为兜底)。 */
    private static final List<String> RED_FLAGS = List.of(
        "体罚", "厌恶疗法", "电击", "束缚", "禁食", "打骂", "关禁闭", "羞辱");

    private final RagRetriever ragRetriever;
    private final AiGateway aiGateway;

    public IepService(RagRetriever ragRetriever, AiGateway aiGateway) {
        this.ragRetriever = ragRetriever;
        this.aiGateway = aiGateway;
    }

    public Iep generateDraft(String childName, String schoolName, String assessmentConclusion) {
        return generateDraft(childName, schoolName, assessmentConclusion, null);
    }

    /** 旧链路:基于评估报告结论生成。 */
    public Iep generateDraft(String childName, String schoolName, String assessmentConclusion,
                             String profileContext) {
        return generate(childName, schoolName, assessmentConclusion, profileContext, null);
    }

    /** 新链路:基于诊断结构化维度 + 报告生成结构化训练 IEP。 */
    public Iep generateFromDiagnosis(String childName, String schoolName,
                                     String diagnosisDimensions, String diagnosisReport,
                                     String profileContext) {
        String conclusion = "诊断维度:\n" + safe(diagnosisDimensions) + "\n诊断报告:\n" + safe(diagnosisReport);
        return generate(childName, schoolName, conclusion, profileContext, diagnosisDimensions);
    }

    /**
     * 二期链路:据阶段评估的方案适配性建议,在原 IEP 基础上优化出新版 IEP。
     * prompt 强调「保留有效内容、优化低效/不适配内容」。
     */
    public Iep generateFromStageEval(String childName, String schoolName,
                                     String stageEvalReport, String prevIepContent,
                                     String diagnosisDimensions) {
        StringBuilder conclusion = new StringBuilder();
        conclusion.append("阶段评估与方案适配性建议:\n").append(safe(stageEvalReport)).append("\n");
        if (prevIepContent != null && !prevIepContent.isBlank()) {
            conclusion.append("原 IEP 方案:\n").append(prevIepContent).append("\n");
        }
        if (diagnosisDimensions != null && !diagnosisDimensions.isBlank()) {
            conclusion.append("诊断维度:\n").append(diagnosisDimensions).append("\n");
        }
        conclusion.append("请在原方案基础上据阶段评估调整:保留有效内容,优化低效/不适配的训练内容。");
        return generate(childName, schoolName, conclusion.toString(), null, diagnosisDimensions);
    }

    private Iep generate(String childName, String schoolName, String conclusion,
                         String profileContext, String dimensionsForQuery) {
        // IEP 个案范例 + 合规/伦理依据,分类召回
        List<KnowledgeDoc> cases = ragRetriever.retrieveByCategory(
            "IEP 干预 训练 " + safe(dimensionsForQuery) + " " + safe(conclusion), "IEP_CASE", 3);
        List<KnowledgeDoc> policies = ragRetriever.retrieveByCategory(
            "政策 伦理 合规 不合理干预 " + safe(conclusion), "POLICY_ETHICS", 2);

        StringBuilder caseKnowledge = new StringBuilder();
        for (KnowledgeDoc d : cases) caseKnowledge.append(d.getContent()).append("\n");
        StringBuilder policyKnowledge = new StringBuilder();
        for (KnowledgeDoc d : policies) policyKnowledge.append(d.getContent()).append("\n");

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是特殊教育 IEP 规划专家。请为 ").append(childName)
            .append("(").append(schoolName).append(")生成个别化教育计划(IEP)草案,供教师编辑定稿。\n");
        prompt.append("按以下五个训练领域分别给出训练内容,每个领域须明确【训练方式】【训练频次】【训练步骤】(如剥珠训练的分步):\n");
        prompt.append("1. 动作训练 2. 语言训练 3. 社交互动 4. 认知培养 5. 生活自理\n");
        prompt.append("每个领域含【长期目标】【短期目标(可量化)】。\n");
        prompt.append("评估/诊断依据:\n").append(conclusion).append("\n");
        if (profileContext != null && !profileContext.isBlank()) {
            prompt.append("既往档案:\n").append(profileContext).append("\n");
        }
        prompt.append("可参考的 IEP 个案范例:\n").append(caseKnowledge);
        prompt.append("【合规与伦理约束(必须遵守)】:\n").append(policyKnowledge);
        prompt.append("严禁包含体罚、厌恶疗法、电击、束缚、禁食等任何伤害性或未经验证的干预手段;");
        prompt.append("目标须符合儿童最大利益、可达成、不歧视;若依据中出现不合理诉求,请规避并给出合规替代。\n");

        String draft = aiGateway.generate(
            new PromptRequest(prompt.toString(), List.of(childName), List.of(schoolName)));

        return new Iep(childName, applyComplianceCheck(draft));
    }

    /** 生成后兜底:命中红线词则前置警示,提示人工复核(prompt 约束为主)。 */
    String applyComplianceCheck(String draft) {
        if (draft == null) return null;
        for (String flag : RED_FLAGS) {
            if (draft.contains(flag)) {
                return "⚠️ 合规提示:本草案可能含不合理干预表述(检出\"" + flag
                    + "\"),请教师重点复核并删改后再定稿。\n\n" + draft;
            }
        }
        return draft;
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
