package com.sellm.child.log;

import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.child.log.dto.ChildLogRequest;
import com.sellm.child.log.dto.ChildLogResponse;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.LogType;
import com.sellm.common.Result;
import com.sellm.security.AccessGuard;
import com.sellm.security.AuthPrincipal;
import com.sellm.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 儿童成长记录(课堂追踪/家校沟通/阶段复盘)。
 * 行级权限复用 AccessGuard.checkChildAccess(老师/管理员限本机构、家长限自己孩子)。
 */
@RestController
@RequestMapping("/api/children/{childId}/logs")
public class ChildLogController {

    private final ChildLogRepository logRepository;
    private final ChildRepository childRepository;
    private final CurrentUser currentUser;
    private final AccessGuard accessGuard;

    public ChildLogController(ChildLogRepository logRepository, ChildRepository childRepository,
                              CurrentUser currentUser, AccessGuard accessGuard) {
        this.logRepository = logRepository;
        this.childRepository = childRepository;
        this.currentUser = currentUser;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public Result<List<ChildLogResponse>> list(@PathVariable Long childId,
                                               @RequestParam(required = false) String type) {
        AuthPrincipal me = currentUser.require();
        requireAccess(me, childId);
        List<ChildLog> logs = (type == null || type.isBlank())
            ? logRepository.listByChild(childId)
            : logRepository.listByChildAndType(childId, type);
        List<ChildLogResponse> out = new ArrayList<>();
        for (ChildLog log : logs) {
            out.add(ChildLogResponse.of(log));
        }
        return Result.ok(out);
    }

    @PostMapping
    public Result<Long> create(@PathVariable Long childId, @RequestBody ChildLogRequest req) {
        AuthPrincipal me = currentUser.require();
        requireAccess(me, childId);
        LogType.validate(req.getLogType()); // 非法/空 → 400
        ChildLog saved = logRepository.save(new ChildLog(null, childId,
            req.getLogType(), req.getContent(), me.getUserId(), null));
        return Result.ok(saved.getId());
    }

    @DeleteMapping("/{logId}")
    public Result<Void> delete(@PathVariable Long childId, @PathVariable Long logId) {
        AuthPrincipal me = currentUser.require();
        requireAccess(me, childId);
        ChildLog log = logRepository.findById(logId);
        if (log == null || !childId.equals(log.getChildId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "记录不存在");
        }
        logRepository.deleteById(logId);
        return Result.ok(null);
    }

    /** 儿童存在 + 行级权限(越权 403)。 */
    private void requireAccess(AuthPrincipal me, Long childId) {
        Child child = childRepository.findById(childId);
        if (child == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "儿童档案不存在");
        }
        accessGuard.checkChildAccess(me, child);
    }
}
