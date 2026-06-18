package com.sellm.integration;

import com.sellm.aigateway.AiModel;
import com.sellm.aigateway.DefaultAiGateway;
import com.sellm.anonymizer.RegexAnonymizer;
import com.sellm.iep.Iep;
import com.sellm.iep.IepService;
import com.sellm.iep.IepStatus;
import com.sellm.rag.KnowledgeDoc;
import com.sellm.rag.RagRetriever;
import com.sellm.report.Report;
import com.sellm.report.ReportService;
import com.sellm.report.ReportStatus;
import com.sellm.scale.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AssessmentToIepFlowTest {

    // 本地模型:断言收到的 prompt 已脱敏,然后回显
    static class AssertingModel implements AiModel {
        @Override
        public String complete(String anonymizedPrompt) {
            assertThat(anonymizedPrompt).doesNotContain("小明").doesNotContain("阳光小学");
            return "[草稿] " + anonymizedPrompt;
        }
    }

    @Test
    void 全链路_评估到IEP草案_身份不泄露且结果还原() {
        RegexAnonymizer anonymizer = new RegexAnonymizer();
        DefaultAiGateway gateway = new DefaultAiGateway(anonymizer, new AssertingModel());
        List<KnowledgeDoc> knowledgeDocs = List.of(
            new KnowledgeDoc("d1", "CARS 解读 轻-中度 建议结构化干预", "手册B"),
            new KnowledgeDoc("d2", "ASD IEP 社交干预 范例 长期短期目标", "范例库")
        );
        // 测试桩:固定返回内存文档,驱动 Report/Iep 服务,无需 Spring/DB
        RagRetriever rag = (query, topK) -> knowledgeDocs.stream().limit(topK).toList();

        // 1. 评估 + 计分
        Scale cars = new Scale("cars", "CARS", "v1",
            List.of(new ScaleItem("q1", "社交", "社交"),
                    new ScaleItem("q2", "沟通", "沟通")),
            new ScoringRule(List.of(
                new ScoreBand(0, 3, "正常", "未见明显异常"),
                new ScoreBand(4, 7, "轻-中度", "建议进一步评估"))));
        AssessmentResult ar = new DefaultScoringEngine().score(cars,
            List.of(new Answer("q1", 2), new Answer("q2", 3)));
        assertThat(ar.getBandLabel()).isEqualTo("轻-中度");

        // 2. 报告草稿
        ReportService reportService = new ReportService(rag, gateway);
        Report report = reportService.generateDraft("小明", "阳光小学", "CARS", ar);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);
        // 结果已还原:含原始姓名
        assertThat(report.getDraft()).contains("小明");

        // 3. 老师定稿
        report.finalizeReport("小明社交沟通存在困难,建议结构化干预");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.FINALIZED);

        // 4. IEP 草案
        IepService iepService = new IepService(rag, gateway);
        Iep iep = iepService.generateDraft("小明", "阳光小学", report.getFinalizedContent());
        assertThat(iep.getStatus()).isEqualTo(IepStatus.DRAFT);
        assertThat(iep.getDraft()).contains("小明");
    }
}
