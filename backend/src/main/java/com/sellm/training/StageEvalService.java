package com.sellm.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.PromptRequest;
import com.sellm.rag.KnowledgeDoc;
import com.sellm.rag.RagRetriever;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段评估领域服务:量化 delta(本周期 vs 上一周期得分,纯 Java)+ AI 叙述
 * (能力提升/未达标/方案适配性建议)。AI 只产草案,人工定稿。
 */
@Service
public class StageEvalService {

    private final RagRetriever ragRetriever;
    private final AiGateway aiGateway;
    private final ObjectMapper json = new ObjectMapper();

    public StageEvalService(RagRetriever ragRetriever, AiGateway aiGateway) {
        this.ragRetriever = ragRetriever;
        this.aiGateway = aiGateway;
    }

    /**
     * 汇总训练数据的指标得分:同 item 取均值(教师多条记录)。
     * @param recordScoresJsons 各 training_record 的 scores JSON([{item,score,maxScore}])
     * @return item → [合计得分, 计数, maxScore] 聚合后的均值结构 JSON
     */
    public String summarizeScores(List<String> recordScoresJsons) {
        Map<String, double[]> agg = new LinkedHashMap<>();  // item → [sumScore, count, maxScore]
        for (String s : recordScoresJsons) {
            if (s == null || s.isBlank()) continue;
            try {
                JsonNode arr = json.readTree(s);
                if (!arr.isArray()) continue;
                for (JsonNode n : arr) {
                    String item = n.path("item").asText("");
                    if (item.isBlank()) continue;
                    double score = n.path("score").asDouble(0);
                    double maxScore = n.path("maxScore").asDouble(0);
                    double[] a = agg.computeIfAbsent(item, k -> new double[3]);
                    a[0] += score; a[1] += 1; a[2] = Math.max(a[2], maxScore);
                }
            } catch (Exception ignore) {}
        }
        ObjectNode out = json.createObjectNode();
        for (Map.Entry<String, double[]> e : agg.entrySet()) {
            double[] a = e.getValue();
            double avg = a[1] > 0 ? a[0] / a[1] : 0;
            ObjectNode it = out.putObject(e.getKey());
            it.put("avgScore", round(avg));
            it.put("maxScore", a[2]);
        }
        return out.toString();
    }

    /**
     * 计算 delta:本期 vs 上期,逐 item 给 delta + 达标判定(达标=avgScore≥maxScore*0.8)。
     * @param currentSummary summarizeScores 的本期结果
     * @param prevSummary    上一周期的 scores_summary(可空,首期为空)
     */
    public String computeDelta(String currentSummary, String prevSummary) {
        ObjectNode result = json.createObjectNode();
        ArrayNode items = result.putArray("items");
        try {
            JsonNode cur = json.readTree(currentSummary == null ? "{}" : currentSummary);
            JsonNode prev = json.readTree(prevSummary == null || prevSummary.isBlank() ? "{}" : prevSummary);
            int improved = 0, total = 0, met = 0;
            var fields = cur.fieldNames();
            while (fields.hasNext()) {
                String item = fields.next();
                JsonNode c = cur.path(item);
                double curAvg = c.path("avgScore").asDouble(0);
                double maxScore = c.path("maxScore").asDouble(0);
                double prevAvg = prev.path(item).path("avgScore").asDouble(Double.NaN);
                ObjectNode it = items.addObject();
                it.put("item", item);
                it.put("current", curAvg);
                boolean reached = maxScore > 0 && curAvg >= maxScore * 0.8;
                it.put("reached", reached);
                if (reached) met++;
                total++;
                if (!Double.isNaN(prevAvg)) {
                    double delta = round(curAvg - prevAvg);
                    it.put("previous", prevAvg);
                    it.put("delta", delta);
                    if (delta > 0) improved++;
                } else {
                    it.putNull("previous");
                    it.putNull("delta");
                }
            }
            result.put("totalItems", total);
            result.put("metItems", met);
            result.put("improvedItems", improved);
        } catch (Exception e) {
            result.put("error", "delta 计算失败");
        }
        return result.toString();
    }

    /** 生成 AI 叙述草案:据 delta + 训练表现 + 个案知识库,产能力提升/未达标/适配性建议。 */
    public String generateNarrative(String childName, String schoolName, String deltaSummary,
                                    String trainingDigest) {
        List<KnowledgeDoc> cases = ragRetriever.retrieveByCategory(
            "训练 评估 提升 适配 " + safe(trainingDigest), "IEP_CASE", 3);
        StringBuilder knowledge = new StringBuilder();
        for (KnowledgeDoc d : cases) knowledge.append(d.getContent()).append("\n");

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是特殊教育康复评估专家。请据本阶段训练数据与量化对比,为 ").append(childName)
            .append("(").append(schoolName).append(")生成阶段性评估报告草案,供教师定稿。\n");
        prompt.append("须包含:【能力提升表现】(结合各指标 delta 说明进步)【未达标训练】(指出未达标项及原因)");
        prompt.append("【方案适配性建议】(原干预方案哪些有效应保留、哪些低效应优化,给具体优化方向)。\n");
        prompt.append("量化对比数据(JSON,delta>0 为进步,reached=true 为达标): ").append(safe(deltaSummary)).append("\n");
        if (trainingDigest != null && !trainingDigest.isBlank()) {
            prompt.append("训练表现摘要: ").append(trainingDigest).append("\n");
        }
        prompt.append("可参考的个案范例:\n").append(knowledge);

        return aiGateway.generate(new PromptRequest(
            prompt.toString(), List.of(childName), List.of(schoolName)));
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
    private static String safe(String s) { return s == null ? "" : s; }
}
