package com.sellm.teaching;

import com.sellm.agentcommon.SmartLayerException;
import com.sellm.anonymizer.AnonymizationException;
import com.sellm.anonymizer.AnonymizationResult;
import com.sellm.anonymizer.Anonymizer;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.storage.ObjectStorage;
import com.sellm.teaching.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Transactional
public class TeachingAppService {

    private final LessonPlanRepository planRepo;
    private final CoursewareRepository cwRepo;
    private final SmartLayerClient smartLayer;
    private final Anonymizer anonymizer;
    private final ObjectStorage storage;

    public TeachingAppService(LessonPlanRepository planRepo, CoursewareRepository cwRepo,
                              SmartLayerClient smartLayer, Anonymizer anonymizer, ObjectStorage storage) {
        this.planRepo = planRepo;
        this.cwRepo = cwRepo;
        this.smartLayer = smartLayer;
        this.anonymizer = anonymizer;
        this.storage = storage;
    }

    // ---- 教案 ----
    public PlanResponse generatePlan(Long userId, GeneratePlanRequest req) {
        if (req.getIepContent() == null || req.getIepContent().isBlank())
            throw new BusinessException(ErrorCode.INVALID_INPUT, "IEP 内容不能为空");
        // 1. 先落 DRAFT(content 空)
        LessonPlan p = new LessonPlan();
        p.setOwnerId(userId);
        p.setChildId(req.getChildId());
        p.setClassId(req.getClassId());
        p.setSourceIepId(req.getSourceIepId());
        p.setScene(req.getScene());
        p.setMode(req.getMode());
        p.setDisorderType(req.getDisorderType());
        p.setStatus("DRAFT");
        planRepo.save(p);
        // 2. 脱敏 → Python → 还原
        String content;
        try {
            AnonymizationResult anon = anonymizer.anonymize(req.getIepContent(), List.of(), List.of());
            String aiText = smartLayer.generate("lesson_plan", anon.getAnonymizedText(),
                req.getDisorderType(), req.getScene(), req.getMode());
            content = anonymizer.restore(aiText, anon.getRestoreMap());
        } catch (AnonymizationException ae) {
            throw new BusinessException(ErrorCode.ANONYMIZATION_FAILED, "脱敏校验未通过,已阻断出网");
        } catch (SmartLayerException se) {
            content = "AI 生成失败,可重试或手动撰写。";
        }
        p.setAiDraft(content);
        p.setContent(content);
        planRepo.update(p);
        return new PlanResponse(p.getId(), p.getStatus(), p.getContent(), p.getAiDraft());
    }

    public PlanResponse editPlan(Long userId, Long id, EditRequest req) {
        LessonPlan p = requireOwnedPlan(userId, id);
        if ("FINALIZED".equals(p.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "已定稿不可编辑");
        if (req.getContent() == null || req.getContent().isBlank())
            throw new BusinessException(ErrorCode.INVALID_INPUT, "内容不能为空");
        p.setContent(req.getContent());
        planRepo.update(p);
        return new PlanResponse(p.getId(), p.getStatus(), p.getContent(), p.getAiDraft());
    }

    public PlanResponse finalizePlan(Long userId, Long id) {
        LessonPlan p = requireOwnedPlan(userId, id);
        p.setStatus("FINALIZED");
        planRepo.update(p);
        return new PlanResponse(p.getId(), p.getStatus(), p.getContent(), p.getAiDraft());
    }

    public PlanResponse getPlan(Long userId, Long id) {
        LessonPlan p = requireOwnedPlan(userId, id);
        return new PlanResponse(p.getId(), p.getStatus(), p.getContent(), p.getAiDraft());
    }

    public List<PlanResponse> listPlans(Long userId) {
        return planRepo.listByOwner(userId).stream()
            .map(p -> new PlanResponse(p.getId(), p.getStatus(), p.getContent(), p.getAiDraft()))
            .toList();
    }

    // ---- 课件 ----
    public CoursewareResponse generateCourseware(Long userId, GenerateCoursewareRequest req) {
        LessonPlan plan = requireOwnedPlan(userId, req.getLessonPlanId());
        if (!"FINALIZED".equals(plan.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "课件须基于已定稿教案");
        Courseware c = new Courseware();
        c.setOwnerId(userId);
        c.setLessonPlanId(plan.getId());
        c.setDisorderType(plan.getDisorderType());
        c.setFormat(req.getFormat() == null ? "TEXT" : req.getFormat());
        c.setStatus("DRAFT");
        cwRepo.save(c);
        String content;
        try {
            AnonymizationResult anon = anonymizer.anonymize(plan.getContent(), List.of(), List.of());
            String aiText = smartLayer.generate("courseware", anon.getAnonymizedText(),
                plan.getDisorderType(), plan.getScene(), plan.getMode());
            content = anonymizer.restore(aiText, anon.getRestoreMap());
        } catch (AnonymizationException ae) {
            throw new BusinessException(ErrorCode.ANONYMIZATION_FAILED, "脱敏校验未通过,已阻断出网");
        } catch (SmartLayerException se) {
            content = "AI 生成失败,可重试或手动撰写。";
        }
        c.setAiDraft(content);
        c.setContent(content);
        cwRepo.update(c);
        return toCwResponse(c);
    }

    public CoursewareResponse editCourseware(Long userId, Long id, EditRequest req) {
        Courseware c = requireOwnedCourseware(userId, id);
        if ("FINALIZED".equals(c.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "已定稿不可编辑");
        if (req.getContent() == null || req.getContent().isBlank())
            throw new BusinessException(ErrorCode.INVALID_INPUT, "内容不能为空");
        c.setContent(req.getContent());
        cwRepo.update(c);
        return toCwResponse(c);
    }

    public CoursewareResponse finalizeCourseware(Long userId, Long id) {
        Courseware c = requireOwnedCourseware(userId, id);
        // 产物落对象存储(仅 finalize)
        String key = "courseware/" + c.getId() + "." + c.getFormat().toLowerCase();
        storage.put(key, c.getContent().getBytes(StandardCharsets.UTF_8),
            "HTML".equalsIgnoreCase(c.getFormat()) ? "text/html" : "text/plain");
        c.setStorageKey(key);
        c.setStatus("FINALIZED");
        cwRepo.update(c);
        return toCwResponse(c);
    }

    public CoursewareResponse getCourseware(Long userId, Long id) {
        return toCwResponse(requireOwnedCourseware(userId, id));
    }

    // ---- helpers ----
    private LessonPlan requireOwnedPlan(Long userId, Long id) {
        LessonPlan p = planRepo.findById(id);
        if (p == null) throw new BusinessException(ErrorCode.NOT_FOUND, "教案不存在");
        if (!p.getOwnerId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该教案");
        return p;
    }

    private Courseware requireOwnedCourseware(Long userId, Long id) {
        Courseware c = cwRepo.findById(id);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "课件不存在");
        if (!c.getOwnerId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该课件");
        return c;
    }

    private CoursewareResponse toCwResponse(Courseware c) {
        return new CoursewareResponse(c.getId(), c.getLessonPlanId(), c.getStatus(),
            c.getContent(), c.getStorageKey(), c.getFormat());
    }
}
