package com.sellm.diagnosis;

import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.PromptRequest;
import com.sellm.rag.KnowledgeDoc;
import com.sellm.rag.RagRetriever;
import com.sellm.scale.Scale;
import com.sellm.scale.ScaleItem;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 诊断领域服务:聚合多模态识别文本 + 结构化训练表现输入 + 量表知识库 → AiGateway 生成
 * 结构化维度(能力等级/现存障碍/能力缺陷)+ 叙述报告草案。AI 只产草案,人工定稿。
 * prompt 要求模型在【维度结构】段输出可解析块,【诊断报告】段输出叙述,便于拆分落库。
 */
@Service
public class DiagnosisService {

    /** 分隔符:模型按此切分"结构化维度"与"叙述报告"两段。 */
    static final String SECTION_DIMENSIONS = "===维度结构===";
    static final String SECTION_REPORT = "===诊断报告===";

    private final RagRetriever ragRetriever;
    private final AiGateway aiGateway;

    public DiagnosisService(RagRetriever ragRetriever, AiGateway aiGateway) {
        this.ragRetriever = ragRetriever;
        this.aiGateway = aiGateway;
    }

    /**
     * @param childName       儿童姓名(脱敏屏蔽用)
     * @param schoolName      机构名(脱敏屏蔽用)
     * @param scale           关联量表(可空;非空则按维度引导)
     * @param recognizedText  多模态识别聚合文本(影像描述/语音转写/笔记)
     * @param structuredInput 结构化训练表现(JSON 文本,如剥珠正确率/眼神互动)
     * @return [0]=结构化维度文本, [1]=诊断报告草案
     */
    public String[] generate(String childName, String schoolName, Scale scale,
                             String recognizedText, String structuredInput) {
        String disorder = scale != null ? scale.getDisorderType() : "特殊儿童";
        List<KnowledgeDoc> docs = ragRetriever.retrieveByCategory(
            "能力维度 等级 障碍 " + (disorder == null ? "" : disorder) + " " + safe(recognizedText),
            "SCALE_SYSTEM", 3);

        StringBuilder knowledge = new StringBuilder();
        for (KnowledgeDoc doc : docs) {
            knowledge.append(doc.getContent()).append("\n");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是特殊教育康复诊断专家。请据以下儿童训练表现与素材,结合量表体系知识,");
        prompt.append("分析其各能力维度(动作、语言沟通、社会交往、认知理解、生活自理)的能力等级、现存障碍、能力缺陷。\n");
        prompt.append("严格分两段输出,先输出 ").append(SECTION_DIMENSIONS).append(" 行,其后逐维度列出'维度: 能力等级 | 现存障碍 | 能力缺陷';");
        prompt.append("再输出 ").append(SECTION_REPORT).append(" 行,其后给出综合诊断报告(叙述,供教师定稿)。\n");
        prompt.append("儿童: ").append(childName).append("(").append(schoolName).append(")\n");
        if (scale != null) {
            prompt.append("关联量表: ").append(scale.getName()).append(",维度参考:");
            for (ScaleItem it : scale.getItems()) {
                prompt.append(it.getDimension()).append("/");
            }
            prompt.append("\n");
        }
        if (structuredInput != null && !structuredInput.isBlank()) {
            prompt.append("结构化训练表现: ").append(structuredInput).append("\n");
        }
        prompt.append("多模态素材识别: ").append(safe(recognizedText)).append("\n");
        prompt.append("可参考的量表体系知识:\n").append(knowledge);

        String result = aiGateway.generate(new PromptRequest(
            prompt.toString(), List.of(childName), List.of(schoolName)));

        return splitSections(result);
    }

    /** 按分隔符拆分;解析失败降级:维度段为空、报告段为全文。 */
    String[] splitSections(String result) {
        if (result == null) return new String[]{"", ""};
        int di = result.indexOf(SECTION_DIMENSIONS);
        int ri = result.indexOf(SECTION_REPORT);
        if (di >= 0 && ri > di) {
            String dims = result.substring(di + SECTION_DIMENSIONS.length(), ri).trim();
            String report = result.substring(ri + SECTION_REPORT.length()).trim();
            return new String[]{dims, report};
        }
        // 降级:模型未按格式,整体当报告
        return new String[]{"", result.trim()};
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
