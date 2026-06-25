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

import java.util.List;

@Service
public class FamilyIepAppService {

    private final ChildRepository childRepository;
    private final ReportRecordRepository reportRepository;
    private final FamilyIepService familyIepService;
    private final FamilyIepRepository recordRepository;
    private final OrganizationRepository organizationRepository;
    private final CurrentUser currentUser;
    private final AccessGuard accessGuard;

    public FamilyIepAppService(ChildRepository childRepository, ReportRecordRepository reportRepository,
                               FamilyIepService familyIepService, FamilyIepRepository recordRepository,
                               OrganizationRepository organizationRepository,
                               CurrentUser currentUser, AccessGuard accessGuard) {
        this.childRepository = childRepository;
        this.reportRepository = reportRepository;
        this.familyIepService = familyIepService;
        this.recordRepository = recordRepository;
        this.organizationRepository = organizationRepository;
        this.currentUser = currentUser;
        this.accessGuard = accessGuard;
    }

    public FamilyIep generate(Long childId, String parentGoal) {
        Child child = childRepository.findById(childId);
        if (child == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "儿童档案不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), child);   // 行级:家长仅自己孩子
        // 取该儿童最新定稿评估报告作为结论(listByChild 已按 id DESC)
        String conclusion = null;
        for (ReportRecord r : reportRepository.listByChild(childId)) {
            if ("FINALIZED".equals(r.getStatus()) && r.getFinalizedContent() != null) {
                conclusion = r.getFinalizedContent();
                break;
            }
        }
        if (conclusion == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请等待评估报告定稿后再生成家庭 IEP");
        }
        String schoolName = organizationRepository.nameOf(child.getOrgId());
        String draft = familyIepService.generateDraft(child.getName(), schoolName, parentGoal, conclusion);
        return recordRepository.save(new FamilyIep(null, childId, currentUser.require().getUserId(),
            parentGoal, draft, null, "DRAFT"));
    }

    public FamilyIep get(Long id) {
        FamilyIep r = recordRepository.findById(id);
        if (r == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "家庭 IEP 不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), childRepository.findById(r.getChildId()));
        return r;
    }

    public List<FamilyIep> listByChild(Long childId) {
        accessGuard.checkChildAccess(currentUser.require(), childRepository.findById(childId));
        return recordRepository.listByChild(childId);
    }

    public FamilyIep finalizePlan(Long id, String content) {
        FamilyIep existing = recordRepository.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "家庭 IEP 不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), childRepository.findById(existing.getChildId()));
        if ("FINALIZED".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "家庭 IEP 已定稿,不可重复定稿");
        }
        recordRepository.finalizePlan(id, content);
        return recordRepository.findById(id);
    }
}
