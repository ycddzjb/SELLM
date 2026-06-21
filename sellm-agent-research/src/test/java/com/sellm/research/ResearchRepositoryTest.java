package com.sellm.research;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ResearchRepositoryTest {

    @Autowired ReliabilityCalcRepository calcRepo;
    @Autowired ResearchProposalRepository proposalRepo;

    @Test
    void 信效度记录保存回填id并按owner查询() {
        ReliabilityCalc c = new ReliabilityCalc();
        c.setOwnerId(7L); c.setDataset("[[1,2],[3,4]]");
        c.setMethod("cronbach+splithalf+itemtotal"); c.setResult("{\"alpha\":0.8}");
        ReliabilityCalc saved = calcRepo.save(c);
        assertNotNull(saved.getId());
        List<ReliabilityCalc> mine = calcRepo.listByOwner(7L);
        assertEquals(1, mine.size());
        assertEquals("cronbach+splithalf+itemtotal", mine.get(0).getMethod());
    }

    @Test
    void 课题书保存更新content与status() {
        ResearchProposal p = new ResearchProposal();
        p.setOwnerId(7L); p.setTopic("融合教育研究");
        p.setStatus("DRAFT"); p.setContent("v1");
        Long id = proposalRepo.save(p).getId();
        ResearchProposal loaded = proposalRepo.findById(id);
        loaded.setContent("v2"); loaded.setStatus("FINALIZED");
        proposalRepo.update(loaded);
        ResearchProposal after = proposalRepo.findById(id);
        assertEquals("v2", after.getContent());
        assertEquals("FINALIZED", after.getStatus());
    }
}
