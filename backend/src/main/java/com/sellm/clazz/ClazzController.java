package com.sellm.clazz;

import com.sellm.clazz.dto.ClazzRequest;
import com.sellm.clazz.dto.ClazzResponse;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import com.sellm.security.AuthPrincipal;
import com.sellm.security.CurrentUser;
import com.sellm.security.Role;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/classes")
public class ClazzController {

    private final ClazzRepository repository;
    private final CurrentUser currentUser;

    public ClazzController(ClazzRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @PostMapping
    public Result<Long> create(@RequestBody ClazzRequest req) {
        AuthPrincipal me = currentUser.require();
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "班级名称不能为空");
        }
        // 归属:orgId 取创建者所属机构(管理员本机构,不信任请求里的 orgId)
        Clazz saved = repository.save(new Clazz(null, req.getName(), me.getOrgId(), req.getDisorderTypes()));
        return Result.ok(saved.getId());
    }

    @GetMapping
    public Result<List<ClazzResponse>> list(@RequestParam(required = false) Long orgId) {
        AuthPrincipal me = currentUser.require();
        // 超管:必须带 orgId 查指定机构(简化);管理员:本机构
        Long targetOrgId = me.getRole() == Role.SUPER_ADMIN ? orgId : me.getOrgId();
        List<ClazzResponse> out = new ArrayList<>();
        if (targetOrgId == null) {
            return Result.ok(out);   // 超管未带 orgId,返回空列表
        }
        for (Clazz c : repository.listByOrg(targetOrgId)) {
            out.add(toResponse(c));
        }
        return Result.ok(out);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ClazzRequest req) {
        AuthPrincipal me = currentUser.require();
        Clazz existing = repository.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "班级不存在");
        }
        checkOrgAccess(me, existing);
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "班级名称不能为空");
        }
        // 归属不变,只改可编辑字段
        repository.update(new Clazz(id, req.getName(), existing.getOrgId(), req.getDisorderTypes()));
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AuthPrincipal me = currentUser.require();
        Clazz existing = repository.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "班级不存在");
        }
        checkOrgAccess(me, existing);
        repository.deleteById(id);
        return Result.ok(null);
    }

    /** 行级:管理员只能操作本机构班级;超管放行任意。 */
    private void checkOrgAccess(AuthPrincipal me, Clazz clazz) {
        if (me.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (clazz.getOrgId() == null || !clazz.getOrgId().equals(me.getOrgId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权操作他机构班级");
        }
    }

    private ClazzResponse toResponse(Clazz c) {
        return new ClazzResponse(c.getId(), c.getName(), c.getOrgId(), c.getDisorderTypes());
    }
}
