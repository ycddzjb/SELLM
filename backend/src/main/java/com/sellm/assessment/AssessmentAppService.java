package com.sellm.assessment;

import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.scale.*;
import com.sellm.security.AccessGuard;
import com.sellm.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssessmentAppService {

    private final ChildRepository childRepository;
    private final ScaleRepository scaleRepository;
    private final ScoringEngine scoringEngine;
    private final AssessmentRepository assessmentRepository;
    private final CurrentUser currentUser;
    private final AccessGuard accessGuard;

    public AssessmentAppService(ChildRepository childRepository, ScaleRepository scaleRepository,
                                ScoringEngine scoringEngine, AssessmentRepository assessmentRepository,
                                CurrentUser currentUser, AccessGuard accessGuard) {
        this.childRepository = childRepository;
        this.scaleRepository = scaleRepository;
        this.scoringEngine = scoringEngine;
        this.assessmentRepository = assessmentRepository;
        this.currentUser = currentUser;
        this.accessGuard = accessGuard;
    }

    public Assessment submit(Long childId, String scaleId, List<Answer> answers) {
        Child child = childRepository.findById(childId);
        if (child == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "儿童档案不存在");
        }
        accessGuard.checkChildAccess(currentUser.require(), child);   // 行级权限:越权→403
        Scale scale = scaleRepository.findById(scaleId);
        if (scale == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "量表不存在");
        }
        AssessmentResult result = scoringEngine.score(scale, answers); // 校验失败抛 ScoringException(全局 advice 转 400)
        return assessmentRepository.save(new Assessment(null, childId, scaleId,
            result.getTotalScore(), result.getBandLabel(), result.getInterpretation()));
    }
}
