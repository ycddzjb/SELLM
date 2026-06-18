package com.sellm.context;

import com.sellm.aigateway.AiGateway;
import com.sellm.aigateway.AiModel;
import com.sellm.anonymizer.Anonymizer;
import com.sellm.child.ChildRepository;
import com.sellm.common.crypto.FieldCipher;
import com.sellm.iep.IepService;
import com.sellm.rag.DbRagRetriever;
import com.sellm.rag.RagRetriever;
import com.sellm.report.ReportService;
import com.sellm.scale.ScaleRepository;
import com.sellm.scale.ScoringEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Autowired
    private RagRetriever ragRetriever;
    @Autowired
    private ReportService reportService;
    @Autowired
    private IepService iepService;
    @Autowired
    private ChildRepository childRepository;
    @Autowired
    private ScaleRepository scaleRepository;
    @Autowired
    private AiGateway aiGateway;
    @Autowired
    private AiModel aiModel;
    @Autowired
    private Anonymizer anonymizer;
    @Autowired
    private FieldCipher fieldCipher;
    @Autowired
    private ScoringEngine scoringEngine;

    @Test
    void 上下文成功启动且关键bean装配() {
        assertThat(reportService).isNotNull();
        assertThat(iepService).isNotNull();
        assertThat(childRepository).isNotNull();
        assertThat(scaleRepository).isNotNull();
        assertThat(aiGateway).isNotNull();
        assertThat(aiModel).isNotNull();
        assertThat(anonymizer).isNotNull();
        assertThat(fieldCipher).isNotNull();
        assertThat(scoringEngine).isNotNull();
    }

    @Test
    void RagRetriever默认实现为DbRagRetriever() {
        assertThat(ragRetriever).isInstanceOf(DbRagRetriever.class);
    }
}
