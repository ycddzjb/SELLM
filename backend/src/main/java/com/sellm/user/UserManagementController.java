package com.sellm.user;

import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.clazz.Clazz;
import com.sellm.clazz.ClazzRepository;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import com.sellm.parent.ParentProfile;
import com.sellm.parent.ParentProfileRepository;
import com.sellm.parent.ParentProfileRow;
import com.sellm.security.AuthPrincipal;
import com.sellm.security.CurrentUser;
import com.sellm.security.Role;
import com.sellm.user.dto.ChangePasswordRequest;
import com.sellm.user.dto.CreateUserRequest;
import com.sellm.user.dto.ParentResponse;
import com.sellm.user.dto.UserResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserManagementController {

    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final ClazzRepository clazzRepository;
    private final TeacherClassMapper teacherClassMapper;
    private final ParentProfileRepository parentProfileRepository;
    private final ChildRepository childRepository;

    public UserManagementController(UserRepository userRepository, CurrentUser currentUser,
                                    ClazzRepository clazzRepository, TeacherClassMapper teacherClassMapper,
                                    ParentProfileRepository parentProfileRepository,
                                    ChildRepository childRepository) {
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.clazzRepository = clazzRepository;
        this.teacherClassMapper = teacherClassMapper;
        this.parentProfileRepository = parentProfileRepository;
        this.childRepository = childRepository;
    }

    @PostMapping
    @Transactional
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
        // 建老师时绑定班级(多选);校验每个班级都属目标机构,防越权绑他机构班级
        if (targetRole == Role.TEACHER && req.getClassIds() != null) {
            for (Long classId : req.getClassIds()) {
                Clazz clazz = clazzRepository.findById(classId);
                if (clazz == null || !targetOrg.equals(clazz.getOrgId())) {
                    throw new BusinessException(ErrorCode.ACCESS_DENIED, "班级不属于本机构");
                }
                teacherClassMapper.insert(saved.getId(), classId);
            }
        }
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

    // 机构管理者:本机构家长列表(完整字段:姓名/儿童/关系/班级;无 profile 的旧家长退化为基础字段)
    @GetMapping("/parents")
    public Result<List<ParentResponse>> parents() {
        AuthPrincipal me = currentUser.require();
        List<ParentResponse> result = new ArrayList<>();
        for (ParentProfileRow row : parentProfileRepository.listByOrg(me.getOrgId())) {
            result.add(ParentResponse.full(row));
        }
        return Result.ok(result);
    }

    // 老师:看分派给自己的待审家长(含姓名/儿童/关系,用于审核判断)
    @GetMapping("/pending")
    public Result<List<ParentResponse>> pendingParents() {
        AuthPrincipal me = currentUser.require();
        List<ParentResponse> result = new ArrayList<>();
        for (ParentProfileRow row : parentProfileRepository.listPendingByTeacher(me.getUserId())) {
            result.add(ParentResponse.full(row));
        }
        return Result.ok(result);
    }

    // 审核通过:PUT /api/users/{id}/approve(老师;行级:仅分派给自己的家长)
    @PutMapping("/{id}/approve")
    @Transactional
    public Result<Void> approve(@PathVariable Long id) {
        AuthPrincipal me = currentUser.require();
        AppUser target = requireAssignedPendingParent(me, id);
        ParentProfile profile = parentProfileRepository.findByUserId(id);
        // 审核通过:创建儿童档案(guardian=家长,org=家长机构),回填 child_id
        Child child = childRepository.save(new Child(null,
            profile == null ? null : profile.getChildName(),
            profile == null ? null : profile.getChildDisorderType(),
            target.getOrgId(), target.getId()));
        if (profile != null) {
            parentProfileRepository.updateChildId(id, child.getId());
        }
        userRepository.updateStatus(id, "ACTIVE");
        return Result.ok(null);
    }

    // 审核拒绝:PUT /api/users/{id}/reject(老师;行级;置 REJECTED,不建儿童)
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        AuthPrincipal me = currentUser.require();
        requireAssignedPendingParent(me, id);
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

    // 行级校验:目标是 PENDING 家长,且 parent_profile.assigned_teacher_id == 当前老师(否则 403)
    private AppUser requireAssignedPendingParent(AuthPrincipal me, Long targetId) {
        AppUser target = userRepository.findById(targetId);
        if (target == null || target.getRole() != Role.PARENT) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权审核该账号");
        }
        ParentProfile profile = parentProfileRepository.findByUserId(targetId);
        if (profile == null || profile.getAssignedTeacherId() == null
                || !profile.getAssignedTeacherId().equals(me.getUserId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权审核该账号(非指派给你)");
        }
        if (!"PENDING".equals(target.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "该账号不是待审核状态");
        }
        return target;
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
