package com.sellm.user;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import com.sellm.security.AuthPrincipal;
import com.sellm.security.CurrentUser;
import com.sellm.security.Role;
import com.sellm.user.dto.ChangePasswordRequest;
import com.sellm.user.dto.CreateUserRequest;
import com.sellm.user.dto.UserResponse;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserManagementController {

    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public UserManagementController(UserRepository userRepository, CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @PostMapping
    public Result<Long> create(@RequestBody CreateUserRequest req) {
        AuthPrincipal me = currentUser.require();
        if (userRepository.findByUsername(req.getUsername()) != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "用户名已存在");
        }
        Role targetRole = req.getRole();
        Long targetOrg;
        if (me.getRole() == Role.SUPER_ADMIN) {
            // 超管:可建 MANAGER(及其他),归属请求指定的机构
            if (req.getOrgId() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "请指定机构");
            }
            targetOrg = req.getOrgId();
        } else if (me.getRole() == Role.MANAGER) {
            // 机构管理者:只能建 TEACHER/PARENT,归属本机构(忽略请求里的 orgId,防越权)
            if (targetRole != Role.TEACHER && targetRole != Role.PARENT) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "机构管理者只能创建老师或家长");
            }
            targetOrg = me.getOrgId();
        } else {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权创建账号");
        }
        // 上级亲手创建的账号一律 ACTIVE(无需审核)
        AppUser saved = userRepository.register(
            req.getUsername(), req.getPassword(), targetRole, targetOrg, "ACTIVE");
        return Result.ok(saved.getId());
    }

    @GetMapping
    public Result<List<UserResponse>> list() {
        AuthPrincipal me = currentUser.require();
        List<AppUser> users = (me.getRole() == Role.SUPER_ADMIN)
            ? userRepository.listAll()
            : userRepository.listByOrg(me.getOrgId());
        return Result.ok(map(users));
    }

    // 机构管理者:本机构待审家长列表
    @GetMapping("/pending")
    public Result<List<UserResponse>> pendingParents() {
        AuthPrincipal me = currentUser.require();
        return Result.ok(map(userRepository.listPendingByOrg(me.getOrgId())));
    }

    // 审核通过:PUT /api/users/{id}/approve
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        AuthPrincipal me = currentUser.require();
        requireSameOrgTarget(me, id);
        userRepository.updateStatus(id, "ACTIVE");
        return Result.ok(null);
    }

    // 审核拒绝:PUT /api/users/{id}/reject(置 REJECTED,登录校验 !=ACTIVE 已挡)
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        AuthPrincipal me = currentUser.require();
        requireSameOrgTarget(me, id);
        userRepository.updateStatus(id, "REJECTED");
        return Result.ok(null);
    }

    // 全角色:改自己密码
    @PutMapping("/me/password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest req) {
        AuthPrincipal me = currentUser.require();
        AppUser self = userRepository.findById(me.getUserId());
        if (self == null || !userRepository.matches(req.getOldPassword(), self.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "原密码错误");
        }
        userRepository.changePassword(me.getUserId(), req.getNewPassword());
        return Result.ok(null);
    }

    // 行级校验:目标存在且与当前用户同机构,否则拒绝(防跨机构越权审核)
    private void requireSameOrgTarget(AuthPrincipal me, Long targetId) {
        AppUser target = userRepository.findById(targetId);
        if (target == null || me.getOrgId() == null
                || !me.getOrgId().equals(target.getOrgId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权审核该账号");
        }
    }

    // AppUser → UserResponse(不暴露 passwordHash)
    private List<UserResponse> map(List<AppUser> users) {
        List<UserResponse> result = new ArrayList<>();
        for (AppUser u : users) {
            result.add(new UserResponse(u.getId(), u.getUsername(), u.getRole(),
                u.getOrgId(), u.getStatus()));
        }
        return result;
    }
}
