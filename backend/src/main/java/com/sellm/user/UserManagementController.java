package com.sellm.user;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import com.sellm.security.AuthPrincipal;
import com.sellm.security.CurrentUser;
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
        // 新账号强制归属创建者所在机构(不信任请求里的 orgId);role 取请求
        AppUser saved = userRepository.register(
            req.getUsername(), req.getPassword(), req.getRole(), me.getOrgId());
        return Result.ok(saved.getId());
    }
}
