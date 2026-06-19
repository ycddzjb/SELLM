package com.sellm.user;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import com.sellm.security.AuthPrincipal;
import com.sellm.security.CurrentUser;
import com.sellm.security.Role;
import com.sellm.user.dto.CreateUserRequest;
import org.springframework.web.bind.annotation.*;

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
}
