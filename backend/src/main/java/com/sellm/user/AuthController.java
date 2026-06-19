package com.sellm.user;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
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

    public AuthController(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public Result<Long> register(@RequestBody RegisterRequest req) {
        if (userRepository.findByUsername(req.getUsername()) != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "用户名已存在");
        }
        // 公开注册一律只产 PARENT、无机构;老师/管理者由 MANAGER 经 /api/users 创建
        AppUser saved = userRepository.register(
            req.getUsername(), req.getPassword(), Role.PARENT, null);
        return Result.ok(saved.getId());
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req) {
        AppUser user = userRepository.findByUsername(req.getUsername());
        if (user == null || !userRepository.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "用户名或密码错误");
        }
        String token = jwtService.issue(
            user.getUsername(), user.getRole().name(), user.getId(), user.getOrgId());
        return Result.ok(new LoginResponse(token, user.getRole().name()));
    }
}
