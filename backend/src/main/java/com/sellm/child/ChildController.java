package com.sellm.child;

import com.sellm.child.dto.ChildRequest;
import com.sellm.child.dto.ChildResponse;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import com.sellm.scale.Scale;
import com.sellm.scale.ScaleRepository;
import com.sellm.scale.dto.ScaleResponse;
import com.sellm.security.AccessGuard;
import com.sellm.security.AuthPrincipal;
import com.sellm.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/children")
public class ChildController {

    private final ChildRepository repository;
    private final CurrentUser currentUser;
    private final AccessGuard accessGuard;
    private final ScaleRepository scaleRepository;

    public ChildController(ChildRepository repository, CurrentUser currentUser, AccessGuard accessGuard,
                           ScaleRepository scaleRepository) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.accessGuard = accessGuard;
        this.scaleRepository = scaleRepository;
    }

    @PostMapping
    public Result<Long> create(@RequestBody ChildRequest req) {
        AuthPrincipal me = currentUser.require();
        // 建档归属:orgId 以创建者所属机构为准(老师/管理者),忽略请求里的 orgId 越权设定
        Long orgId = me.getOrgId();
        Child child = new Child(null, req.getName(), req.getDisorderType(),
            orgId, req.getGuardianUserId());
        applyExtended(child, req);
        Child saved = repository.save(child);
        return Result.ok(saved.getId());
    }

    @GetMapping("/{id}")
    public Result<ChildResponse> get(@PathVariable Long id) {
        AuthPrincipal me = currentUser.require();
        Child c = repository.findById(id);
        if (c == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "儿童档案不存在");
        }
        accessGuard.checkChildAccess(me, c);   // 越权 → 403
        return Result.ok(toResponse(c));
    }

    /** 按儿童障碍类型推荐适配量表(老师仍可手选全部)。行级权限。 */
    @GetMapping("/{id}/recommended-scales")
    public Result<List<ScaleResponse>> recommendedScales(@PathVariable Long id) {
        AuthPrincipal me = currentUser.require();
        Child c = repository.findById(id);
        if (c == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "儿童档案不存在");
        }
        accessGuard.checkChildAccess(me, c);
        List<ScaleResponse> out = new ArrayList<>();
        String type = c.getDisorderType();
        if (type != null && !type.isBlank()) {
            for (Scale s : scaleRepository.listByDisorderType(type)) {
                out.add(ScaleResponse.of(s, false));
            }
        }
        return Result.ok(out);
    }

    @GetMapping
    public Result<List<ChildResponse>> list() {
        AuthPrincipal me = currentUser.require();
        List<ChildResponse> out = new ArrayList<>();
        for (Child c : repository.findAll()) {
            if (accessGuard.canAccess(me, c)) {   // 行级过滤:只返回有权的
                out.add(toResponse(c));
            }
        }
        return Result.ok(out);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ChildRequest req) {
        AuthPrincipal me = currentUser.require();
        Child existing = repository.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "儿童档案不存在");
        }
        accessGuard.checkChildAccess(me, existing);
        // 保持归属不变(orgId/guardian 沿用现有),只改可编辑字段
        Child updated = new Child(id, req.getName(), req.getDisorderType(),
            existing.getOrgId(), existing.getGuardianUserId());
        applyExtended(updated, req);
        repository.update(updated);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AuthPrincipal me = currentUser.require();
        Child existing = repository.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "儿童档案不存在");
        }
        accessGuard.checkChildAccess(me, existing);
        repository.deleteById(id);
        return Result.ok(null);
    }

    private ChildResponse toResponse(Child c) {
        return ChildResponse.of(c);
    }

    /** 把请求里的扩展字段装配到 Child(create/update 复用)。 */
    private void applyExtended(Child child, ChildRequest req) {
        child.setBaselineSummary(req.getBaselineSummary());
        child.setAnnualIepSummary(req.getAnnualIepSummary());
        child.setMonthlyGoal(req.getMonthlyGoal());
        child.setReassessDate(req.getReassessDate());
        child.setIepDueDate(req.getIepDueDate());
        child.setInterventionProgress(req.getInterventionProgress());
    }
}
