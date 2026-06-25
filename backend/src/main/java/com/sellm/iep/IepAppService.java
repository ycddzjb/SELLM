package com.sellm.iep;

import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.org.OrganizationRepository;
import com.sellm.report.ReportRecord;
import com.sellm.report.ReportRecordRepository;
import com.sellm.security.AccessGuard;
import com.sellm.security.CurrentUser;
import org.springframework.stereotype.Service;

@Service
public class IepAppService {

    private final ReportRecordRepository reportRepository;
    private final ChildRepository childRepository;
    private final IepService iepService;            // 计划一领域服务
    private final IepRecordRepository recordRepository;
    private final OrganizationRepository organizationRepository;
    private final com.sellm.diagnosis.DiagnosisRepository diagnosisRepository;
    private final CurrentUser currentUser;
    private final AccessGuard accessGuard;

    public IepAppService(ReportRecordRepository reportRepository, ChildRepository childRepository,
                         IepService iepService, IepRecordRepository recordRepository,
                         OrganizationRepository organizationRepository,
                         com.sellm.diagnosis.DiagnosisRepository diagnosisRepository,
                         CurrentUser currentUser, AccessGuard accessGuard) {
        this.reportRepository = reportRepository;
        this.childRepository = childRepository;
        this.iepService = iepService;
        this.recordRepository = recordRepository;
        this.organizationRepository = organizationRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.currentUser = currentUser;
        this.accessGuard = accessGuard;
    }

    public IepRecord generate(Long reportId) {
        ReportRecord report = reportRepository.findById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "报告不存在");
        }
        Child child = childRepository.findById(report.getChildId());
        if (child == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "儿童档案不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), child);   // 行级权限
        String schoolName = organizationRepository.nameOf(child.getOrgId());
        // 评估结论:优先用定稿内容,否则用草稿
        String conclusion = report.getFinalizedContent() != null
            ? report.getFinalizedContent() : report.getDraft();
        Iep domain = iepService.generateDraft(child.getName(), schoolName, conclusion, profileContext(child));
        return recordRepository.save(new IepRecord(null, reportId, null, report.getChildId(),
            domain.getDraft(), null, "DRAFT"));
    }

    /** 入口:优先诊断链路(diagnosisId),否则旧报告链路(reportId)。 */
    public IepRecord generate(Long reportId, Long diagnosisId) {
        if (diagnosisId != null) {
            return generateFromDiagnosis(diagnosisId);
        }
        if (reportId != null) {
            return generate(reportId);
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "请提供诊断 ID 或报告 ID");
    }

    /** 新链路:基于多模态诊断结果(结构化维度 + 报告)生成结构化训练 IEP。 */
    public IepRecord generateFromDiagnosis(Long diagnosisId) {
        com.sellm.diagnosis.Diagnosis diag = diagnosisRepository.findById(diagnosisId);
        if (diag == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "诊断不存在");
        }
        Child child = childRepository.findById(diag.getChildId());
        if (child == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "儿童档案不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), child);   // 行级权限
        String schoolName = organizationRepository.nameOf(child.getOrgId());
        // 诊断报告:优先定稿,否则草案
        String report = diag.getFinalizedContent() != null ? diag.getFinalizedContent() : diag.getDraft();
        Iep domain = iepService.generateFromDiagnosis(
            child.getName(), schoolName, diag.getDimensions(), report, profileContext(child));
        return recordRepository.save(new IepRecord(null, null, diagnosisId, diag.getChildId(),
            domain.getDraft(), null, "DRAFT"));
    }

    /** 把儿童档案扩展字段拼成模型可用上下文(非 PII)。 */
    private String profileContext(Child child) {
        StringBuilder sb = new StringBuilder();
        if (child.getAnnualIepSummary() != null && !child.getAnnualIepSummary().isBlank()) {
            sb.append("年度 IEP 方案: ").append(child.getAnnualIepSummary()).append("\n");
        }
        if (child.getMonthlyGoal() != null && !child.getMonthlyGoal().isBlank()) {
            sb.append("月度干预目标: ").append(child.getMonthlyGoal()).append("\n");
        }
        if (child.getInterventionProgress() != null && !child.getInterventionProgress().isBlank()) {
            sb.append("干预进度: ").append(child.getInterventionProgress()).append("\n");
        }
        return sb.toString();
    }

    public IepRecord get(Long id) {
        IepRecord r = recordRepository.findById(id);
        if (r == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "IEP 不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), childRepository.findById(r.getChildId()));
        return r;
    }

    public IepRecord finalizePlan(Long id, String content) {
        IepRecord existing = recordRepository.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "IEP 不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), childRepository.findById(existing.getChildId()));
        if ("FINALIZED".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "IEP 已定稿,不可重复定稿");
        }
        recordRepository.finalizePlan(id, content);
        return recordRepository.findById(id);
    }

    public java.util.List<IepRecord> listByChild(Long childId) {
        Child child = childRepository.findById(childId);
        accessGuard.checkChildAccess(currentUser.require(), child); // child 为 null → canAccess false → 403
        return recordRepository.listByChild(childId);
    }
}
