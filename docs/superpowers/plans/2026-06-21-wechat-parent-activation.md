# 计划:管理端审核微信家长(分配机构 + 绑孩子激活)

> 日期:2026-06-21 · 分支:feature/special-edu-llm(dev_workspace)
> 打通 P10 微信登录闭环:微信家长首登落 PENDING(无机构/无 profile/有 wx_openid),由 MANAGER 审核激活
> 红线见 [[sellm-ai-privacy-redlines]];状态见 [[special-edu-llm-p0-done]]

## 背景与关键差异
现有审核流(UserManagementController.approve)是**老师专属、ParentProfile 驱动**:依赖注册时填的 childName/childDisorderType/assignedTeacherId。微信家长**没有 ParentProfile**(首登只建 app_user:PENDING + wx_openid + 机构可能为空),故无法走老师流。需新增 **MANAGER 驱动的微信家长激活端点**:管理者补全机构(若空)+ 孩子信息 → 建孩子档案 → 激活。

## 后端

### 1. 查询:本机构(及无机构)待激活微信家长
- `AppUserMapper.findPendingWeChat`:`SELECT ... FROM app_user WHERE status='PENDING' AND wx_openid IS NOT NULL AND (org_id = #{orgId} OR org_id IS NULL)`。
  - 机构管理者能看本机构 + 尚未分配机构(org_id null)的微信待审家长(后者由其认领激活并落本机构)。
- `UserRepository.listPendingWeChat(orgId)` → List<AppUser>(含 wxOpenid)。
- XML 对应 select。

### 2. 激活端点:`PUT /api/users/{id}/activate-wechat`(MANAGER)
- DTO `ActivateWeChatRequest`:`childName`、`childDisorderType`(单码或 CSV,DisorderType.validateCsv 校验)、`classId`(可空)。
- AppService 逻辑(`@Transactional`):
  1. 行级校验:目标存在 + role=PARENT + status=PENDING + 有 wx_openid(非微信家长拒绝,走老师流);且(目标 org == 管理者 org)或(目标 org 为空 → 认领,置为管理者 org)。
  2. classId 若填,校验属管理者机构(防越权,镜像 create 逻辑)。
  3. 建 Child(name=childName 加密、disorderType、org=管理者机构、guardian=该家长 userId)。
  4. 写 ParentProfile(userId、childName、childDisorderType、classId、childId 回填;name/relationship/assignedTeacher 留空——微信家长无这些)→ 复用 ParentProfileRepository.save。
  5. 若目标 org 为空,`userRepository.updateRoleOrgStatus` 落机构;否则 `updateStatus`。统一置 ACTIVE。
- 拒绝端点复用现有 reject?现有 reject 是 TEACHER 角色 + assignedTeacher 校验,微信家长无指派老师 → 新增 `PUT /api/users/{id}/reject-wechat`(MANAGER,校验本机构/无机构的微信 PENDING,置 REJECTED)。

### 3. SecurityConfig(有序,具体在通配前)
- 在 `/api/users/*/approve|reject`(TEACHER)规则**之后、`/api/users/**` POST 通配之前**插:
  - `PUT /api/users/*/activate-wechat`、`PUT /api/users/*/reject-wechat` → `hasRole("MANAGER")`。
  - `GET /api/users/pending-wechat` → `hasRole("MANAGER")`。
- 注意:`/api/users/**` 当前只有 POST 通配 hasAnyRole(SUPER_ADMIN,MANAGER);PUT 无通配兜底,故新 PUT 必须显式列(否则落 `/api/**` authenticated,任何登录用户可调 → 越权)。**这是关键安全点。**

### 4. 控制器
- UserManagementController 加 `pendingWeChat()`(GET)、`activateWeChat(id, req)`、`rejectWeChat(id)`,注入已有依赖(childRepository/parentProfileRepository/clazzRepository 都已注入)。
- 返回:pending-wechat 返回简单 DTO(userId/username/wxOpenid 脱敏展示——username 即 wx_openid 派生,够用;不泄露额外 PII)。复用 UserResponse 即可(id/username/role/orgId/status)。

## 管理前端(MANAGER 区块)
- `api/users.js` 加 `listPendingWeChat()`、`activateWeChat(id, payload)`、`rejectWeChat(id)`。
- UsersView.vue 的 MANAGER 区块新增卡片「微信家长待激活」:
  - 表格列:账号(username)、状态;操作列「激活」「拒绝」。
  - 「激活」弹 el-dialog 表单:孩子姓名(必填)、障碍类型(el-select 多选 DISORDER_TYPES,提交 join ',')、班级(el-select 可空,本机构班级)→ 调 activateWeChat。
  - 复用现有 statusType/statusLabel、DISORDER_TYPES、loadClasses。
- 激活成功后刷新列表 + 用户表。

## 测试(backend,镜像 WeChatLoginApiTest/AuthApiTest)
新增 `WeChatActivateApiTest`(@SpringBootTest @ActiveProfiles("test")):
- 种子:经 UserRepository.registerWeChat 建微信 PENDING(org=null 或本机构);AuthTestSupport.registerAndLogin 建 MANAGER(org=1)取 token。
- 用例:
  1. MANAGER 激活无机构微信家长 → 200,落本机构 + ACTIVE + 建孩子(childRepository 查到 guardian=该家长)+ ParentProfile.childId 回填。
  2. 激活后该家长可微信登录拿 token(串 WeChatLoginApiTest 的激活路径或直接断 status=ACTIVE)。
  3. 行级:MANAGER 激活他机构(org=2)微信家长 → 403。
  4. 非微信家长(无 wx_openid 的密码 PENDING)走 activate-wechat → 400(应走老师流)。
  5. 非 PENDING(已 ACTIVE)→ 400。
  6. 拒绝:reject-wechat → REJECTED。
  7. classId 不属本机构 → 403。
  8. 非法障碍码 → 400。
  9. 安全:TEACHER/PARENT 调 activate-wechat → 403(SecurityConfig 端点级)。

## 验证与收尾
1. `mvn clean install` 全 reactor 10 模块 SUCCESS;backend 246 + 新增(~9);qa16/teaching15/research20/aids18 不受影响。
2. `cd frontend && npm run build` 通过(改前端必跑)。
3. 变更记录:`.claude/CLAUDE_CHANGES.md` 追加一条(FEATURE)。
4. 记忆:更新 [[special-edu-llm-p0-done]](微信家长审核闭环已通)。

## 不做(YAGNI)
- 微信家长改绑孩子/多孩子(首版一家长一孩子,镜像现有);
- 管理者把微信家长重新指派给老师走老师流(两条独立流,不混);
- 小程序端「待审核」状态轮询提示(首登已返"账号待审核"toast,够用)。
