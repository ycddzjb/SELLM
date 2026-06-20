package com.sellm.user;

import com.sellm.clazz.Clazz;
import com.sellm.clazz.ClazzRepository;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.DisorderType;
import com.sellm.common.Relationship;
import com.sellm.common.Result;
import com.sellm.org.OrganizationRepository;
import com.sellm.parent.ParentProfile;
import com.sellm.parent.ParentProfileRepository;
import com.sellm.security.JwtService;
import com.sellm.security.Role;
import com.sellm.user.dto.LoginRequest;
import com.sellm.user.dto.LoginResponse;
import com.sellm.user.dto.RegisterRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final OrganizationRepository organizationRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final ClazzRepository clazzRepository;

    public AuthController(UserRepository userRepository, JwtService jwtService,
                          OrganizationRepository organizationRepository,
                          ParentProfileRepository parentProfileRepository,
                          ClazzRepository clazzRepository) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.organizationRepository = organizationRepository;
        this.parentProfileRepository = parentProfileRepository;
        this.clazzRepository = clazzRepository;
    }

    @PostMapping("/register")
    @Transactional
    public Result<Long> register(@RequestBody RegisterRequest req) {
        if (req.getOrgId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请选择所属机构");
        }
        if (req.getAssignedTeacherId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请选择所在班级的老师/康复师");
        }
        if (userRepository.findByUsername(req.getUsername()) != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "用户名已存在");
        }
        // 校验关系码、儿童残障类型码合法(非法 → IllegalArgumentException → 400)
        Relationship.validate(req.getRelationship());
        DisorderType.validateCsv(req.getChildDisorderType());
        // 校验所选老师确为本机构 TEACHER(防乱指派)
        AppUser teacher = userRepository.findById(req.getAssignedTeacherId());
        if (teacher == null || teacher.getRole() != Role.TEACHER
                || !req.getOrgId().equals(teacher.getOrgId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "所选老师不属于该机构");
        }
        // 校验班级属本机构(可选填,填了就校验)
        if (req.getClassId() != null) {
            Clazz clazz = clazzRepository.findById(req.getClassId());
            if (clazz == null || !req.getOrgId().equals(clazz.getOrgId())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "所选班级不属于该机构");
            }
        }
        // 公开注册:家长,选机构,待审核
        AppUser saved = userRepository.register(
            req.getUsername(), req.getPassword(), Role.PARENT, req.getOrgId(), "PENDING");
        // 暂存家长扩展信息 + 待建儿童信息(姓名加密)
        parentProfileRepository.save(new ParentProfile(
            saved.getId(), req.getName(), req.getRelationship(), req.getAssignedTeacherId(),
            req.getChildName(), req.getChildDisorderType(), req.getClassId(), null));
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
