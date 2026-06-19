package com.sellm.user;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import com.sellm.org.OrganizationRepository;
import com.sellm.security.JwtService;
import com.sellm.security.Role;
import com.sellm.user.dto.LoginRequest;
import com.sellm.user.dto.LoginResponse;
import com.sellm.user.dto.RegisterRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final OrganizationRepository organizationRepository;

    public AuthController(UserRepository userRepository, JwtService jwtService,
                          OrganizationRepository organizationRepository) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.organizationRepository = organizationRepository;
    }

    @PostMapping("/register")
    public Result<Long> register(@RequestBody RegisterRequest req) {
        if (req.getOrgId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请选择所属机构");
        }
        if (userRepository.findByUsername(req.getUsername()) != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "用户名已存在");
        }
        // 公开注册:家长,选机构,待审核
        AppUser saved = userRepository.register(
            req.getUsername(), req.getPassword(), Role.PARENT, req.getOrgId(), "PENDING");
        return Result.ok(saved.getId());
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req) {
        AppUser user = userRepository.findByUsername(req.getUsername());
        if (user == null || !userRepository.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "账号待审核或已停用,暂不能登录");
        }
        String token = jwtService.issue(
            user.getUsername(), user.getRole().name(), user.getId(), user.getOrgId());
        String orgName = organizationRepository.nameOf(user.getOrgId());
        return Result.ok(new LoginResponse(
            token, user.getRole().name(), user.getUsername(), user.getOrgId(), orgName));
    }
}
