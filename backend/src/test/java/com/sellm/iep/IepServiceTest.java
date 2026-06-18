package com.sellm.iep;

import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.PromptRequest;
import com.sellm.rag.KnowledgeDoc;
import com.sellm.rag.RagRetriever;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IepServiceTest {

    @Test
    void 生成IEP草案状态为DRAFT() {
        RagRetriever rag = mock(RagRetriever.class);
        when(rag.retrieve(anyString(), anyInt()))
            .thenReturn(List.of(new KnowledgeDoc("i1", "ASD 社交干预 IEP 范例", "范例库")));
        AiGateway gateway = mock(AiGateway.class);
        when(gateway.generate(any(PromptRequest.class))).thenReturn("小明的 IEP 草案");

        IepService service = new IepService(rag, gateway);
        Iep iep = service.generateDraft("小明", "阳光小学",
            "轻-中度,社交沟通存在困难");

        assertThat(iep.getStatus()).isEqualTo(IepStatus.DRAFT);
        assertThat(iep.getDraft()).isEqualTo("小明的 IEP 草案");
    }

    @Test
    void prompt含评估结论与召回范例并传身份信息供脱敏() {
        RagRetriever rag = mock(RagRetriever.class);
        when(rag.retrieve(anyString(), anyInt()))
            .thenReturn(List.of(new KnowledgeDoc("i1", "ASD 社交干预 IEP 范例", "范例库")));
        AiGateway gateway = mock(AiGateway.class);
        when(gateway.generate(any(PromptRequest.class))).thenReturn("草案");

        IepService service = new IepService(rag, gateway);
        service.generateDraft("小明", "阳光小学", "轻-中度,社交沟通存在困难");

        ArgumentCaptor<PromptRequest> captor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(gateway).generate(captor.capture());
        PromptRequest req = captor.getValue();
        assertThat(req.getPrompt()).contains("社交沟通存在困难").contains("IEP 范例");
        assertThat(req.getNames()).contains("小明");
        assertThat(req.getSchools()).contains("阳光小学");
    }

    @Test
    void 定稿后状态为FINALIZED() {
        Iep iep = new Iep("小明", "草案");
        iep.finalizePlan("终稿内容");
        assertThat(iep.getStatus()).isEqualTo(IepStatus.FINALIZED);
    }
}
