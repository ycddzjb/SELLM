package com.sellm.diagnosis;

import com.sellm.anonymizer.AnonymizationException;
import com.sellm.anonymizer.AnonymizationResult;
import com.sellm.anonymizer.Anonymizer;
import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.diagnosis.dto.GenerateDiagnosisRequest;
import com.sellm.multimodal.MediaRecognizer;
import com.sellm.org.OrganizationRepository;
import com.sellm.scale.Scale;
import com.sellm.scale.ScaleRepository;
import com.sellm.security.AccessGuard;
import com.sellm.security.CurrentUser;
import com.sellm.storage.ObjectStorage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 诊断编排:行级权限 + 多模态识别聚合 + 出网脱敏硬阻断 + AI 产 DRAFT 人工定稿。
 * 不加类级 @Transactional:含出网 HTTP;诊断先落 DRAFT 再出网生成(对齐项目其他模块)。
 */
@Service
public class DiagnosisAppService {

    private final DiagnosisRepository repo;
    private final DiagnosisMediaRepository mediaRepo;
    private final DiagnosisService diagnosisService;
    private final MediaRecognizer mediaRecognizer;
    private final ChildRepository childRepository;
    private final ScaleRepository scaleRepository;
    private final OrganizationRepository organizationRepository;
    private final ObjectStorage objectStorage;
    private final Anonymizer anonymizer;
    private final CurrentUser currentUser;
    private final AccessGuard accessGuard;

    public DiagnosisAppService(DiagnosisRepository repo, DiagnosisMediaRepository mediaRepo,
                               DiagnosisService diagnosisService, MediaRecognizer mediaRecognizer,
                               ChildRepository childRepository, ScaleRepository scaleRepository,
                               OrganizationRepository organizationRepository, ObjectStorage objectStorage,
                               Anonymizer anonymizer, CurrentUser currentUser, AccessGuard accessGuard) {
        this.repo = repo;
        this.mediaRepo = mediaRepo;
        this.diagnosisService = diagnosisService;
        this.mediaRecognizer = mediaRecognizer;
        this.childRepository = childRepository;
        this.scaleRepository = scaleRepository;
        this.organizationRepository = organizationRepository;
        this.objectStorage = objectStorage;
        this.anonymizer = anonymizer;
        this.currentUser = currentUser;
        this.accessGuard = accessGuard;
    }

    /** 建诊断(DRAFT,尚未生成内容),供随后挂素材 + 生成。 */
    public Diagnosis create(Long childId, String scaleId, String structuredInput) {
        Child child = requireChild(childId);
        return repo.save(new Diagnosis(null, childId, currentUser.require().getUserId(),
            scaleId, structuredInput, null, null, null, "DRAFT"));
    }

    /**
     * 给诊断挂多模态素材并识别。素材字节存对象存储;noteText 出网前脱敏硬阻断;
     * 识别文本(transcript)落库供生成聚合。
     */
    public DiagnosisMedia addMedia(Long diagnosisId, String mediaType, byte[] bytes,
                                   String filename, String noteText, List<String> subjectNames) {
        Diagnosis d = requireOwnedDraft(diagnosisId);
        String objectKey = null;
        if (bytes != null && bytes.length > 0) {
            objectKey = "diagnosis/" + d.getChildId() + "/" + System.nanoTime() + "_" + sanitize(filename);
            objectStorage.put(objectKey, bytes, null);
        }
        // 出网前脱敏(noteText 可能含姓名等 PII),失败硬阻断
        String safeNote = noteText;
        if (safeNote != null && !safeNote.isBlank()) {
            try {
                AnonymizationResult anon = anonymizer.anonymize(safeNote, safeNames(subjectNames), List.of());
                safeNote = anon.getAnonymizedText();
            } catch (AnonymizationException ae) {
                throw new BusinessException(ErrorCode.ANONYMIZATION_FAILED, "笔记脱敏未通过,已阻断出网");
            }
        }
        // 识别(IMAGE/VIDEO→vision, AUDIO→ASR, TEXT→原文)
        String transcript = mediaRecognizer.recognize(mediaType, bytes, safeNote);
        return mediaRepo.save(new DiagnosisMedia(null, diagnosisId, mediaType, objectKey, transcript, safeNote));
    }
    /**
     * 生成诊断:聚合该诊断已挂素材的识别文本 + 结构化输入 + 量表 RAG → AiGateway →
     * 结构化维度 + 报告草案,回写 DRAFT。
     */
    public Diagnosis generate(Long diagnosisId, List<String> subjectNames) {
        Diagnosis d = requireOwnedDraft(diagnosisId);
        Child child = requireChild(d.getChildId());
        String schoolName = organizationRepository.nameOf(child.getOrgId());
        Scale scale = d.getScaleId() == null ? null : scaleRepository.findById(d.getScaleId());

        // 聚合已识别素材文本
        StringBuilder recognized = new StringBuilder();
        for (DiagnosisMedia m : mediaRepo.listByDiagnosis(diagnosisId)) {
            if (m.getTranscript() != null && !m.getTranscript().isBlank()) {
                recognized.append("[").append(m.getMediaType()).append("] ")
                    .append(m.getTranscript()).append("\n");
            }
        }

        List<String> names = (subjectNames != null && !subjectNames.isEmpty())
            ? safeNames(subjectNames)
            : (child.getName() != null && !child.getName().isBlank() ? List.of(child.getName()) : List.of());

        String[] sections;
        try {
            sections = diagnosisService.generate(
                child.getName() == null ? "该儿童" : child.getName(),
                schoolName, scale, recognized.toString(), d.getInputSummary());
        } catch (com.sellm.anonymizer.AnonymizationException ae) {
            throw new BusinessException(ErrorCode.ANONYMIZATION_FAILED, "脱敏校验未通过,已阻断出网");
        }
        repo.updateResult(diagnosisId, sections[0], sections[1]);
        return repo.findById(diagnosisId);
    }

    public Diagnosis edit(Long id, String draft) {
        Diagnosis d = requireOwned(id);
        if ("FINALIZED".equals(d.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "已定稿不可编辑");
        }
        if (draft == null || draft.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "诊断报告不能为空");
        }
        repo.updateDraft(id, draft);
        return repo.findById(id);
    }

    public Diagnosis finalizeDiagnosis(Long id, String content) {
        Diagnosis d = requireOwned(id);
        if ("FINALIZED".equals(d.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "诊断已定稿,不可重复定稿");
        }
        String finalContent = (content != null && !content.isBlank()) ? content : d.getDraft();
        repo.finalizeDiagnosis(id, finalContent);
        return repo.findById(id);
    }

    public Diagnosis get(Long id) {
        return requireOwned(id);
    }

    public List<Diagnosis> listByChild(Long childId) {
        Child child = childRepository.findById(childId);
        accessGuard.checkChildAccess(currentUser.require(), child);   // null → 403
        return repo.listByChild(childId);
    }

    public List<DiagnosisMedia> listMedia(Long diagnosisId) {
        requireOwned(diagnosisId);
        return mediaRepo.listByDiagnosis(diagnosisId);
    }

    // ── 内部校验 ──
    private Child requireChild(Long childId) {
        Child child = childRepository.findById(childId);
        if (child == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "儿童档案不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), child);   // 行级权限
        return child;
    }

    private Diagnosis requireOwned(Long id) {
        Diagnosis d = repo.findById(id);
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "诊断不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), childRepository.findById(d.getChildId()));
        return d;
    }

    private Diagnosis requireOwnedDraft(Long id) {
        Diagnosis d = requireOwned(id);
        if ("FINALIZED".equals(d.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "诊断已定稿,不可再修改");
        }
        return d;
    }

    private static List<String> safeNames(List<String> names) {
        if (names == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String n : names) if (n != null && !n.isBlank()) out.add(n);
        return out;
    }

    private static String sanitize(String filename) {
        if (filename == null || filename.isBlank()) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
