package com.sellm.report;

import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.PromptRequest;
import com.sellm.rag.KnowledgeDoc;
import com.sellm.rag.RagRetriever;
import com.sellm.scale.AssessmentResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {

    @Test
    void 生成报告草稿状态为DRAFT且含召回知识() {
        RagRetriever rag = mock(RagRetriever.class);
        when(rag.retrieve(anyString(), anyInt()))
            .thenReturn(List.of(new KnowledgeDoc("d2", "CARS 解读知识", "手册B")));

        AiGateway gateway = mock(AiGateway.class);
        when(gateway.generate(any(PromptRequest.class)))
            .thenReturn("小明的评估报告草稿内容");

        ReportService service = new ReportService(rag, gateway);
        AssessmentResult ar = new AssessmentResult(5.0, "轻-中度", "建议进一步评估");

        Report report = service.generateDraft("小明", "阳光小学", "CARS", ar);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);
        assertThat(report.getDraft()).isEqualTo("小明的评估报告草稿内容");
        assertThat(report.getChildName()).isEqualTo("小明");
    }

    @Test
    void prompt包含召回知识与得分并传入身份信息供网关脱敏() {
        RagRetriever rag = mock(RagRetriever.class);
        when(rag.retrieve(anyString(), anyInt()))
            .thenReturn(List.of(new KnowledgeDoc("d2", "CARS 解读知识", "手册B")));

        AiGateway gateway = mock(AiGateway.class);
        when(gateway.generate(any(PromptRequest.class))).thenReturn("草稿");

        ReportService service = new ReportService(rag, gateway);
        service.generateDraft("小明", "阳光小学", "CARS",
            new AssessmentResult(5.0, "轻-中度", "建议进一步评估"));

        ArgumentCaptor<PromptRequest> captor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(gateway).generate(captor.capture());
        PromptRequest req = captor.getValue();
        assertThat(req.getPrompt()).contains("CARS 解读知识").contains("轻-中度").contains("5");
        assertThat(req.getNames()).contains("小明");
        assertThat(req.getSchools()).contains("阳光小学");
    }

    @Test
    void 老师定稿后状态变为FINALIZED() {
        Report report = new Report("小明", "草稿");
        report.finalizeReport("老师修改后的终稿");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.FINALIZED);
        assertThat(report.getFinalizedContent()).isEqualTo("老师修改后的终稿");
    }

    @Test
    void prompt纳入儿童档案上下文且仍传脱敏名单() {
        RagRetriever rag = mock(RagRetriever.class);
        when(rag.retrieve(anyString(), anyInt()))
            .thenReturn(List.of(new KnowledgeDoc("d2", "CARS 解读知识", "手册B")));
        AiGateway gateway = mock(AiGateway.class);
        when(gateway.generate(any(PromptRequest.class))).thenReturn("草稿");

        ReportService service = new ReportService(rag, gateway);
        service.generateDraft("小明", "阳光小学", "CARS",
            new AssessmentResult(5.0, "轻-中度", "建议进一步评估"),
            "基线评估: 社交弱\n月度干预目标: 每日跟读\n");

        ArgumentCaptor<PromptRequest> captor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(gateway).generate(captor.capture());
        PromptRequest req = captor.getValue();
        // 档案上下文进入 prompt
        assertThat(req.getPrompt()).contains("基线评估: 社交弱").contains("月度干预目标: 每日跟读");
        // 脱敏名单不变(姓名/校名仍交网关脱敏)
        assertThat(req.getNames()).contains("小明");
        assertThat(req.getSchools()).contains("阳光小学");
    }
}
