package com.sellm.report;

import com.sellm.assessment.Assessment;
import com.sellm.assessment.AssessmentRepository;
import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.org.OrganizationRepository;
import com.sellm.scale.AssessmentResult;
import com.sellm.security.AccessGuard;
import com.sellm.security.CurrentUser;
import org.springframework.stereotype.Service;

@Service
public class ReportAppService {

    private final AssessmentRepository assessmentRepository;
    private final ChildRepository childRepository;
    private final ReportService reportService;          // 计划一领域服务(RAG+AI)
    private final ReportRecordRepository recordRepository;
    private final OrganizationRepository organizationRepository;
    private final CurrentUser currentUser;
    private final AccessGuard accessGuard;

    public ReportAppService(AssessmentRepository assessmentRepository, ChildRepository childRepository,
                            ReportService reportService, ReportRecordRepository recordRepository,
                            OrganizationRepository organizationRepository,
                            CurrentUser currentUser, AccessGuard accessGuard) {
        this.assessmentRepository = assessmentRepository;
        this.childRepository = childRepository;
        this.reportService = reportService;
        this.recordRepository = recordRepository;
        this.organizationRepository = organizationRepository;
        this.currentUser = currentUser;
        this.accessGuard = accessGuard;
    }

    public ReportRecord generate(Long assessmentId) {
        Assessment a = assessmentRepository.findById(assessmentId);
        if (a == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "评估记录不存在");
        }
        Child child = childRepository.findById(a.getChildId());
        if (child == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "儿童档案不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), child);   // 行级权限
        // 真实校名(Child 所属机构);机构缺失时 OrganizationRepository.nameOf 返回兜底名
        String schoolName = organizationRepository.nameOf(child.getOrgId());
        AssessmentResult result = new AssessmentResult(
            a.getTotalScore(), a.getBandLabel(), a.getInterpretation());
        Report domain = reportService.generateDraft(
            child.getName(), schoolName, a.getScaleId(), result);
        // domain.getDraft() 已还原明文,落库
        return recordRepository.save(new ReportRecord(null, assessmentId, a.getChildId(),
            domain.getDraft(), null, "DRAFT"));
    }

    public ReportRecord get(Long reportId) {
        ReportRecord r = recordRepository.findById(reportId);
        if (r == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "报告不存在");
        }
        Child child = childRepository.findById(r.getChildId());
        accessGuard.checkChildAccess(currentUser.require(), child);   // 经 child 归属做行级判定
        return r;
    }

    public ReportRecord finalizeReport(Long reportId, String content) {
        ReportRecord existing = recordRepository.findById(reportId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "报告不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), childRepository.findById(existing.getChildId()));
        recordRepository.finalizeReport(reportId, content);
        return recordRepository.findById(reportId);
    }

    public java.util.List<ReportRecord> listByChild(Long childId) {
        Child child = childRepository.findById(childId);
        accessGuard.checkChildAccess(currentUser.require(), child); // child 为 null → canAccess false → 403
        return recordRepository.listByChild(childId);
    }
}
