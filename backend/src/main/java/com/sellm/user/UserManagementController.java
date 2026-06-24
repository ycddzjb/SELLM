package com.sellm.user;

import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.clazz.Clazz;
import com.sellm.clazz.ClazzRepository;
import com.sellm.common.BusinessException;
import com.sellm.common.DisorderType;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import com.sellm.parent.ParentProfile;
import com.sellm.parent.ParentProfileRepository;
import com.sellm.parent.ParentProfileRow;
import com.sellm.security.AuthPrincipal;
import com.sellm.security.CurrentUser;
import com.sellm.security.Role;
import com.sellm.user.dto.ActivateWeChatRequest;
import com.sellm.user.dto.ChangePasswordRequest;
import com.sellm.user.dto.CreateUserRequest;
import com.sellm.user.dto.ParentResponse;
import com.sellm.user.dto.UpdateUserRequest;
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

    // 机构管理者:本机构(及未分配机构)待激活微信家长列表
    @GetMapping("/pending-wechat")
    public Result<List<UserResponse>> pendingWeChat() {
        AuthPrincipal me = currentUser.require();
        return Result.ok(map(userRepository.listPendingWeChat(me.getOrgId())));
    }

    // 机构管理者:激活微信家长 —— 补孩子信息 → 建档 → 落本机构 + ACTIVE
    @PutMapping("/{id}/activate-wechat")
    @Transactional
    public Result<Void> activateWeChat(@PathVariable Long id, @RequestBody ActivateWeChatRequest req) {
        AuthPrincipal me = currentUser.require();
        AppUser target = requireWeChatPendingParent(me, id);
        if (req.getChildName() == null || req.getChildName().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请填写孩子姓名");
        }
        DisorderType.validateCsv(req.getChildDisorderType());   // 非法码 → IllegalArgumentException → 400
        Long orgId = me.getOrgId();
        // 班级若填,校验属本机构(防越权)
        if (req.getClassId() != null) {
            Clazz clazz = clazzRepository.findById(req.getClassId());
            if (clazz == null || !orgId.equals(clazz.getOrgId())) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "班级不属于本机构");
            }
        }
        // 建儿童档案(guardian=该微信家长,org=管理者机构),姓名加密由 Repository 处理
        Child child = childRepository.save(new Child(null,
            req.getChildName(), req.getChildDisorderType(), orgId, target.getId()));
        // 写家长档案(微信家长无 name/relationship/assignedTeacher;只存孩子信息 + childId 回填)
        parentProfileRepository.save(new ParentProfile(
            target.getId(), null, null, null,
            req.getChildName(), req.getChildDisorderType(), req.getClassId(), child.getId()));
        // 落机构(原可能为空) + 激活
        userRepository.updateRoleOrgStatus(id, Role.PARENT, orgId, "ACTIVE");
        return Result.ok(null);
    }

    // 机构管理者:拒绝微信家长(置 REJECTED,不建儿童)
    @PutMapping("/{id}/reject-wechat")
    public Result<Void> rejectWeChat(@PathVariable Long id) {
        AuthPrincipal me = currentUser.require();
        requireWeChatPendingParent(me, id);
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

    // 超管:编辑用户(状态/角色/机构);字段为 null 则保留原值
    @PutMapping("/{id}")
    @Transactional
    public Result<Void> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest req) {
        AuthPrincipal me = currentUser.require();
        AppUser target = userRepository.findById(id);
        if (target == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "用户不存在");
        }
        Role role = req.getRole() != null ? req.getRole() : target.getRole();
        Long orgId = req.getOrgId() != null ? req.getOrgId() : target.getOrgId();
        String status = (req.getStatus() != null && !req.getStatus().isBlank())
            ? req.getStatus() : target.getStatus();
        userRepository.updateRoleOrgStatus(id, role, orgId, status);
        return Result.ok(null);
    }

    // 超管:软删用户(置 DISABLED,登录校验 status==ACTIVE 即自动失效);禁删自己/最后一个超管
    @DeleteMapping("/{id}")
    @Transactional
    public Result<Void> deleteUser(@PathVariable Long id) {
        AuthPrincipal me = currentUser.require();
        if (id.equals(me.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "不能删除当前登录账号");
        }
        AppUser target = userRepository.findById(id);
        if (target == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "用户不存在");
        }
        if (target.getRole() == Role.SUPER_ADMIN
                && userRepository.countActiveByRole(Role.SUPER_ADMIN.name()) <= 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "不能删除最后一个超级管理员");
        }
        userRepository.updateStatus(id, "DISABLED");
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

    // 行级校验:目标是微信 PENDING 家长(有 wx_openid),且属当前管理者机构或尚未分配机构(认领)
    private AppUser requireWeChatPendingParent(AuthPrincipal me, Long targetId) {
        AppUser target = userRepository.findById(targetId);
        if (target == null || target.getRole() != Role.PARENT) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权激活该账号");
        }
        if (target.getWxOpenid() == null || target.getWxOpenid().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "该账号非微信家长,请走老师审核流");
        }
        // 机构归属:本机构或未分配(未分配则由本管理者认领落库)
        if (target.getOrgId() != null && !target.getOrgId().equals(me.getOrgId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权激活其他机构的家长");
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
