package com.sellm.training;

import com.sellm.anonymizer.AnonymizationException;
import com.sellm.anonymizer.AnonymizationResult;
import com.sellm.anonymizer.Anonymizer;
import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.multimodal.MediaRecognizer;
import com.sellm.security.AccessGuard;
import com.sellm.security.CurrentUser;
import com.sellm.storage.ObjectStorage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 训练周期编排:行级权限 + 多模态训练数据识别 + 出网脱敏硬阻断。
 * 周期串起诊断→IEP→训练数据→阶段评估;seq 自动递增。
 */
@Service
public class TrainingCycleAppService {

    private final TrainingCycleRepository cycleRepo;
    private final TrainingRecordRepository recordRepo;
    private final StageEvalRepository stageEvalRepo;
    private final StageEvalService stageEvalService;
    private final MediaRecognizer mediaRecognizer;
    private final ChildRepository childRepository;
    private final com.sellm.org.OrganizationRepository organizationRepository;
    private final com.sellm.iep.IepService iepService;
    private final com.sellm.iep.IepRecordRepository iepRecordRepository;
    private final com.sellm.diagnosis.DiagnosisRepository diagnosisRepository;
    private final ObjectStorage objectStorage;
    private final Anonymizer anonymizer;
    private final CurrentUser currentUser;
    private final AccessGuard accessGuard;

    public TrainingCycleAppService(TrainingCycleRepository cycleRepo, TrainingRecordRepository recordRepo,
                                   StageEvalRepository stageEvalRepo, StageEvalService stageEvalService,
                                   MediaRecognizer mediaRecognizer, ChildRepository childRepository,
                                   com.sellm.org.OrganizationRepository organizationRepository,
                                   com.sellm.iep.IepService iepService,
                                   com.sellm.iep.IepRecordRepository iepRecordRepository,
                                   com.sellm.diagnosis.DiagnosisRepository diagnosisRepository,
                                   ObjectStorage objectStorage, Anonymizer anonymizer,
                                   CurrentUser currentUser, AccessGuard accessGuard) {
        this.cycleRepo = cycleRepo;
        this.recordRepo = recordRepo;
        this.stageEvalRepo = stageEvalRepo;
        this.stageEvalService = stageEvalService;
        this.mediaRecognizer = mediaRecognizer;
        this.childRepository = childRepository;
        this.organizationRepository = organizationRepository;
        this.iepService = iepService;
        this.iepRecordRepository = iepRecordRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.objectStorage = objectStorage;
        this.anonymizer = anonymizer;
        this.currentUser = currentUser;
        this.accessGuard = accessGuard;
    }

    public TrainingCycle createCycle(Long childId, Long diagnosisId, Long iepId, String title) {
        Child child = requireChild(childId);
        int seq = cycleRepo.nextSeq(childId);
        return cycleRepo.save(new TrainingCycle(null, childId, currentUser.require().getUserId(),
            diagnosisId, iepId, seq, title, "ACTIVE"));
    }

    public TrainingCycle getCycle(Long id) {
        return requireOwnedCycle(id);
    }

    public List<TrainingCycle> listByChild(Long childId) {
        Child child = childRepository.findById(childId);
        accessGuard.checkChildAccess(currentUser.require(), child);   // null → 403
        return cycleRepo.listByChild(childId);
    }

    public TrainingCycle closeCycle(Long id) {
        TrainingCycle c = requireOwnedCycle(id);
        cycleRepo.updateStatus(id, "CLOSED");
        c.setStatus("CLOSED");
        return c;
    }

    /**
     * 给周期挂多模态训练数据:文件存对象存储;noteText 出网前脱敏硬阻断;
     * MediaRecognizer 识别落 transcript;scores 为教师录入/采纳的指标得分 JSON。
     */
    public TrainingRecord addRecord(Long cycleId, String mediaType, byte[] bytes, String filename,
                                    String noteText, String scores, List<String> subjectNames) {
        TrainingCycle c = requireOwnedCycle(cycleId);
        if ("CLOSED".equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "周期已关闭,不可再加训练数据");
        }
        String objectKey = null;
        if (bytes != null && bytes.length > 0) {
            objectKey = "training/" + c.getChildId() + "/" + System.nanoTime() + "_" + sanitize(filename);
            objectStorage.put(objectKey, bytes, null);
        }
        String safeNote = noteText;
        if (safeNote != null && !safeNote.isBlank()) {
            try {
                AnonymizationResult anon = anonymizer.anonymize(safeNote, safeNames(subjectNames), List.of());
                safeNote = anon.getAnonymizedText();
            } catch (AnonymizationException ae) {
                throw new BusinessException(ErrorCode.ANONYMIZATION_FAILED, "笔记脱敏未通过,已阻断出网");
            }
        }
        String transcript = mediaRecognizer.recognize(mediaType, bytes, safeNote);
        return recordRepo.save(new TrainingRecord(null, cycleId, mediaType, objectKey, transcript, safeNote, scores));
    }

    public List<TrainingRecord> listRecords(Long cycleId) {
        requireOwnedCycle(cycleId);
        return recordRepo.listByCycle(cycleId);
    }

    /**
     * 生成阶段评估:汇总本周期训练得分 → 对比上一周期(seq-1)→ 量化 delta + AI 叙述,落 DRAFT。
     */
    public StageEval generateStageEval(Long cycleId, List<String> subjectNames) {
        TrainingCycle c = requireOwnedCycle(cycleId);
        Child child = requireChild(c.getChildId());
        String schoolName = organizationRepository.nameOf(child.getOrgId());

        // 本期得分汇总 + 训练表现摘要
        List<TrainingRecord> records = recordRepo.listByCycle(cycleId);
        java.util.List<String> scoreJsons = new java.util.ArrayList<>();
        StringBuilder digest = new StringBuilder();
        for (TrainingRecord r : records) {
            if (r.getScores() != null) scoreJsons.add(r.getScores());
            if (r.getTranscript() != null && !r.getTranscript().isBlank()) {
                digest.append("[").append(r.getMediaType()).append("] ").append(r.getTranscript()).append("\n");
            }
        }
        String curSummary = stageEvalService.summarizeScores(scoreJsons);

        // 上一周期(seq-1)的得分汇总
        String prevSummary = null;
        if (c.getSeq() > 1) {
            TrainingCycle prevCycle = cycleRepo.findByChildAndSeq(c.getChildId(), c.getSeq() - 1);
            if (prevCycle != null) {
                StageEval prevEval = stageEvalRepo.findByCycle(prevCycle.getId());
                if (prevEval != null) prevSummary = prevEval.getScoresSummary();
            }
        }
        String delta = stageEvalService.computeDelta(curSummary, prevSummary);

        String draft;
        try {
            draft = stageEvalService.generateNarrative(
                child.getName() == null ? "该儿童" : child.getName(), schoolName, delta, digest.toString());
        } catch (AnonymizationException ae) {
            throw new BusinessException(ErrorCode.ANONYMIZATION_FAILED, "脱敏校验未通过,已阻断出网");
        }
        return stageEvalRepo.save(new StageEval(null, cycleId, c.getChildId(),
            curSummary, delta, draft, null, "DRAFT"));
    }

    public StageEval editStageEval(Long evalId, String draft) {
        StageEval e = requireOwnedEval(evalId);
        if ("FINALIZED".equals(e.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "已定稿不可编辑");
        }
        if (draft == null || draft.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "评估报告不能为空");
        }
        stageEvalRepo.updateDraft(evalId, draft);
        return stageEvalRepo.findById(evalId);
    }

    public StageEval finalizeStageEval(Long evalId, String content) {
        StageEval e = requireOwnedEval(evalId);
        if ("FINALIZED".equals(e.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "阶段评估已定稿,不可重复定稿");
        }
        String finalContent = (content != null && !content.isBlank()) ? content : e.getDraft();
        stageEvalRepo.finalizeEval(evalId, finalContent);
        return stageEvalRepo.findById(evalId);
    }

    public StageEval getStageEval(Long evalId) {
        return requireOwnedEval(evalId);
    }

    /** 纵向对比:某 child 各周期阶段评估的得分汇总(供前端画趋势)。 */
    public List<StageEval> compareByChild(Long childId) {
        Child child = childRepository.findById(childId);
        accessGuard.checkChildAccess(currentUser.require(), child);
        List<StageEval> out = new java.util.ArrayList<>();
        for (TrainingCycle c : cycleRepo.listByChild(childId)) {
            StageEval e = stageEvalRepo.findByCycle(c.getId());
            if (e != null) out.add(e);
        }
        return out;
    }

    /**
     * 据本周期阶段评估的方案适配性建议,在原 IEP 基础上优化出新版 IEP(DRAFT),关联本周期。
     * 要求阶段评估已生成(优先用定稿内容,否则草案)。AI 只产草案,人工定稿。
     */
    public com.sellm.iep.IepRecord generateNextIep(Long cycleId) {
        TrainingCycle c = requireOwnedCycle(cycleId);
        Child child = requireChild(c.getChildId());
        String schoolName = organizationRepository.nameOf(child.getOrgId());

        StageEval eval = stageEvalRepo.findByCycle(cycleId);
        if (eval == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请先生成本周期阶段评估");
        }
        String evalReport = eval.getFinalizedContent() != null ? eval.getFinalizedContent() : eval.getDraft();

        String prevIep = null;
        if (c.getIepId() != null) {
            com.sellm.iep.IepRecord old = iepRecordRepository.findById(c.getIepId());
            if (old != null) prevIep = old.getFinalizedContent() != null ? old.getFinalizedContent() : old.getDraft();
        }
        String dims = null;
        if (c.getDiagnosisId() != null) {
            com.sellm.diagnosis.Diagnosis d = diagnosisRepository.findById(c.getDiagnosisId());
            if (d != null) dims = d.getDimensions();
        }

        com.sellm.iep.Iep domain = iepService.generateFromStageEval(
            child.getName() == null ? "该儿童" : child.getName(), schoolName, evalReport, prevIep, dims);

        com.sellm.iep.IepRecord rec = new com.sellm.iep.IepRecord(
            null, null, c.getDiagnosisId(), c.getChildId(), domain.getDraft(), null, "DRAFT");
        rec.setCycleId(cycleId);
        rec = iepRecordRepository.save(rec);
        cycleRepo.updateIepId(cycleId, rec.getId());   // 新 IEP 回挂周期
        return rec;
    }

    private StageEval requireOwnedEval(Long evalId) {
        StageEval e = stageEvalRepo.findById(evalId);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "阶段评估不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), childRepository.findById(e.getChildId()));
        return e;
    }

    // ── 内部校验 ──
    private Child requireChild(Long childId) {
        Child child = childRepository.findById(childId);
        if (child == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "儿童档案不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), child);
        return child;
    }

    private TrainingCycle requireOwnedCycle(Long id) {
        TrainingCycle c = cycleRepo.findById(id);
        if (c == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "训练周期不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), childRepository.findById(c.getChildId()));
        return c;
    }

    private static List<String> safeNames(List<String> names) {
        if (names == null) return List.of();
        return names.stream().filter(n -> n != null && !n.isBlank()).toList();
    }

    private static String sanitize(String filename) {
        if (filename == null || filename.isBlank()) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
