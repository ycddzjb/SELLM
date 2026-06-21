package com.sellm.teaching;

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
class TeachingRepositoryTest {

    @Autowired LessonPlanRepository planRepo;
    @Autowired CoursewareRepository cwRepo;

    @Test
    void 教案保存回填id并能按owner查询() {
        LessonPlan p = new LessonPlan();
        p.setOwnerId(5L); p.setSourceIepId(34L);
        p.setScene("SCHOOL"); p.setMode("ONE_ON_ONE");
        p.setDisorderType("ASD"); p.setAiDraft("draft"); p.setContent("draft");
        p.setStatus("DRAFT");
        LessonPlan saved = planRepo.save(p);
        assertNotNull(saved.getId());

        List<LessonPlan> mine = planRepo.listByOwner(5L);
        assertEquals(1, mine.size());
        assertEquals("SCHOOL", mine.get(0).getScene());
    }

    @Test
    void 教案更新content与status() {
        LessonPlan p = new LessonPlan();
        p.setOwnerId(5L); p.setScene("HOME"); p.setMode("GROUP");
        p.setStatus("DRAFT"); p.setContent("v1");
        Long id = planRepo.save(p).getId();

        LessonPlan loaded = planRepo.findById(id);
        loaded.setContent("v2"); loaded.setStatus("FINALIZED");
        planRepo.update(loaded);

        LessonPlan after = planRepo.findById(id);
        assertEquals("v2", after.getContent());
        assertEquals("FINALIZED", after.getStatus());
    }

    @Test
    void 课件保存回填id并回填storageKey() {
        Courseware c = new Courseware();
        c.setOwnerId(5L); c.setLessonPlanId(1L); c.setDisorderType("ASD");
        c.setContent("cw"); c.setFormat("TEXT"); c.setStatus("DRAFT");
        Long id = cwRepo.save(c).getId();
        assertNotNull(id);

        Courseware loaded = cwRepo.findById(id);
        loaded.setStorageKey("media/cw-1.txt"); loaded.setStatus("FINALIZED");
        cwRepo.update(loaded);
        assertEquals("media/cw-1.txt", cwRepo.findById(id).getStorageKey());
    }
}
