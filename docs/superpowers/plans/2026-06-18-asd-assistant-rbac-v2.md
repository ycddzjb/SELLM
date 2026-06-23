# ASD 助手 — 计划五:四级权限体系 + 家长审核 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把现有三角色(TEACHER/MANAGER/PARENT)权限模型升级为四级:新增 SUPER_ADMIN(超级管理者,跨机构);机构管理者 MANAGER 建老师/家长并审核公开申请的家长;公开注册的家长选机构、待审核、审核前不能登录;全角色可自助改密码。前后端配套。

**Architecture:** 沿用模块化单体 + Vue 管理端。后端:Role 加 SUPER_ADMIN;app_user 加 status(ACTIVE/PENDING);登录校验 status;AccessGuard 让 SUPER_ADMIN 跨机构;用户管理端点按角色分化(超管建机构/MANAGER、MANAGER 建 TEACHER/PARENT + 审核家长、全员改密码);公开注册改为选机构+PENDING;新增公开机构列表端点。前端:用户管理页按当前角色渲染不同能力;注册页(若有)选机构;顶栏已显示身份(上一提交)。测试 H2 + MockMvc。

**Tech Stack:** 后端 Spring Boot 3.2.5 + Java 17 + MyBatis + Spring Security + JWT;前端 Vue 3 + Element Plus。复用计划一~四(master)。

**Source spec:** `docs/superpowers/specs/2026-06-18-asd-assessment-iep-assistant-design.md`(权限模型为本计划细化)

---

## 权限模型 v2(本计划落地)

**四级角色:** `SUPER_ADMIN`、`MANAGER`(机构管理者)、`TEACHER`(老师/康复师)、`PARENT`(家长)。

**用户管理权限矩阵:**
| 角色 | 用户管理能力 |
|---|---|
| SUPER_ADMIN | 查看所有机构 + 所有用户;创建机构;为指定机构创建 MANAGER;改自己密码 |
| MANAGER | 改自己密码;为本机构创建 TEACHER/PARENT;审核本机构待审家长(通过/拒绝) |
| TEACHER | 仅改自己密码 |
| PARENT | 仅改自己密码 |

**账号状态:** `app_user.status` ∈ {ACTIVE, PENDING}。
- 上级亲手创建的账号(超管建 MANAGER、MANAGER 建 TEACHER/PARENT、dev 种子)→ ACTIVE。
- 公开注册的家长 → PENDING(选机构),审核通过 → ACTIVE。
- **登录校验:status != ACTIVE 拒绝登录**(提示"账号待审核")。

**关键变更(从现状收回/调整):**
- MANAGER 不再能建 MANAGER(收归 SUPER_ADMIN);MANAGER 只能建 TEACHER/PARENT。
- 公开注册 PARENT 从即时 ACTIVE → PENDING + 必须选机构(orgId 非空)。
- admin 种子从 MANAGER 升为 SUPER_ADMIN(无机构,orgId=null)。
- AccessGuard:SUPER_ADMIN 跨机构(canAccess 恒 true);其余不变。

**安全红线延续:** 端点级 RBAC + 行级 AccessGuard 双层;创建账号时 orgId/status 由后端强制(不信任客户端);自助改密码只能改自己。

---

## 文件结构(本计划范围)

```
backend/
  src/main/resources/
    schema.sql                  # app_user 加 status 列(Task 1)
    seed-dev.sql / DevSeeder    # admin 升 SUPER_ADMIN(Task 1)
  src/main/java/com/sellm/
    security/Role.java          # 加 SUPER_ADMIN(Task 1)
    security/AccessGuard.java   # SUPER_ADMIN 跨机构(Task 4)
    security/SecurityConfig.java# /api/users、/api/orgs、/api/auth 规则调整(Task 2/5/6)
    user/
      AppUser.java              # 加 status(Task 1)
      AppUserMapper.java + xml  # status 读写、按机构/状态查询、改密码、改状态(Task 1/5/6)
      UserRepository.java       # register 带 status、列表查询、updatePassword、updateStatus(Task 1/5/6)
      AuthController.java       # 注册选机构+PENDING、登录校验 status(Task 2)
      UserManagementController  # 按角色分化:超管建机构/MANAGER、MANAGER 建 TEACHER/PARENT+审核、改密码、用户列表(Task 5/6/7)
      dto/...                   # RegisterRequest 加 orgId、CreateUserRequest、ChangePasswordRequest、审核 DTO、用户/机构列表响应
    org/
      OrganizationController.java# 公开机构列表(注册选机构用)+ 超管建机构/列表(Task 3)
      OrganizationRepository.java# listAll、save(Task 3)
  src/test/java/com/sellm/...   # 各 Task 的 MockMvc/集成测试
frontend/
  src/api/users.js / orgs.js    # 用户管理/机构 API(Task 8)
  src/views/UsersView.vue       # 按角色分化的用户管理页(Task 8)
  src/views/RegisterView.vue    # 家长注册页(选机构)(Task 8,若做公开注册入口)
  src/stores/auth.js / router   # SUPER_ADMIN 菜单(Task 8)
```

---

### Task 1: 数据模型基础 — SUPER_ADMIN 角色 + app_user.status + admin 升级(TDD)

加 SUPER_ADMIN 角色、app_user.status 列、AppUser.status 字段、UserRepository 支持 status 读写;dev 种子 admin 升为 SUPER_ADMIN(无机构)。本任务只动数据层与种子,不改端点逻辑(端点在后续任务)。

**Files:**
- Modify: `backend/src/main/java/com/sellm/security/Role.java`(加 SUPER_ADMIN)
- Modify: `backend/src/main/resources/schema.sql`(app_user 加 status)
- Modify: `backend/src/main/java/com/sellm/user/AppUser.java`(加 status)
- Modify: `backend/src/main/java/com/sellm/user/AppUserMapper.java` + `mybatis/AppUserMapper.xml`(insert/select 带 status)
- Modify: `backend/src/main/java/com/sellm/user/UserRepository.java`(register 带 status 参数;findByUsername 读 status)
- Modify: `backend/src/main/java/com/sellm/config/DevSeeder.java`(admin → SUPER_ADMIN, orgId=null)
- Test: `backend/src/test/java/com/sellm/user/UserRepositoryTest.java`(适配:register 新签名、status)

- [x] **Step 1: Role 加 SUPER_ADMIN**
```java
public enum Role {
    SUPER_ADMIN,  // 超级管理者(跨机构)
    MANAGER,      // 机构管理者
    TEACHER,      // 老师/康复师
    PARENT        // 家长
}
```

- [x] **Step 2: schema.sql app_user 加 status**

把 app_user 的 CREATE TABLE 加一列(放 org_id 后):
```sql
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
```
注:H2/MySQL 都支持列 DEFAULT。已存在的 dev H2 文件库需重建(删 backend/data/ 重启)或靠 schema 的 IF NOT EXISTS——**但 ALTER 加列更稳**:在 CREATE TABLE 之后加 `ALTER TABLE app_user ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';`(与计划三 child guardian_user_id 同样手法,兼容已存在的库)。实现者二选一并说明;推荐 ALTER IF NOT EXISTS 以免毁掉 dev 已有数据。

- [x] **Step 3: AppUser 加 status 字段 + getter/setter**

`AppUser` 加 `private String status;`,全参构造器加 status 参数,并**保留兼容**:给一个不带 status 的构造器委托新构造器(status 默认 "ACTIVE"),避免计划三既有调用(若有)全炸。或更新所有调用点——实现者权衡,但**既有 UserRepositoryTest 必须仍绿**。

- [x] **Step 4: AppUserMapper + XML 带 status**

Mapper insert 参数 map 加 status;resultMap(userMap)加 `<result column="status" property="status"/>`;insert SQL 加 status 列;findByUsername SELECT 加 status。

- [x] **Step 5: UserRepository.register 带 status**

`register(username, rawPassword, role, orgId, status)`(新增 status 参数);**保留旧 4 参 register 委托新版**(status 默认 "ACTIVE"),使计划三既有调用(AuthController/UserManagementController/DevSeeder/测试)不破——它们后续任务会逐步改。findByUsername 组装 AppUser 带 status。

- [x] **Step 6: DevSeeder admin → SUPER_ADMIN**

`userRepository.register("admin", "admin123", Role.SUPER_ADMIN, null, "ACTIVE")`(超管无机构)。
注:dev H2 文件库里已有的 admin 是旧 MANAGER。DevSeeder 是"不存在才建",已存在的不会改。**实现者需在 Step 末尾说明:验证时若 admin 已是旧角色,需删 backend/data/ 重启让种子重建**(dev 数据可丢)。或 DevSeeder 改为"存在则更新 admin 的 role/status 为 SUPER_ADMIN"(幂等纠正)——推荐后者更稳,实现者择一。

- [x] **Step 7: 适配 UserRepositoryTest**

现有测试调 `register(u,p,role,orgId)`(4参)。若保留了 4 参委托则不破;另加断言:新注册用户 status 默认 ACTIVE;register 5 参版能存 PENDING 并读回。

- [x] **Step 8: 全量回归 + 提交**

Run: `cd "D:/works/test/SELLM/backend" && ./mvnw -q test`,期望此前 81 全绿(+ 可能新增断言)。报告实际数。
```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/security/Role.java backend/src/main/resources/schema.sql backend/src/main/java/com/sellm/user/ backend/src/main/resources/mybatis/AppUserMapper.xml backend/src/main/java/com/sellm/config/DevSeeder.java backend/src/test/java/com/sellm/user/UserRepositoryTest.java && git commit -q -m "feat(rbac): 加 SUPER_ADMIN 角色 + app_user.status;admin 升超管"
```

---
### Task 2: 公开注册选机构 + PENDING;登录校验 status(TDD)

公开注册改为:必须选机构(orgId 非空)、强制 PARENT、status=PENDING;登录时 status != ACTIVE 拒绝。

**Files:**
- Modify: `backend/src/main/java/com/sellm/user/dto/RegisterRequest.java`(加 orgId)
- Modify: `backend/src/main/java/com/sellm/user/AuthController.java`(register 选机构+PENDING;login 校验 status)
- Modify: `backend/src/main/java/com/sellm/user/UserRepository.java`(若需:findByUsername 已带 status)
- Test: `backend/src/test/java/com/sellm/auth/AuthApiTest.java`(适配 + 新增待审不能登录)

- [x] **Step 1: RegisterRequest 加 orgId**

加 `private Long orgId;` + getter/setter。

- [x] **Step 2: AuthController.register**
```java
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
```

- [x] **Step 3: AuthController.login 校验 status**

在密码校验通过后、签发 token 前加:
```java
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "账号待审核或已停用,暂不能登录");
        }
```
(AppUser 已有 getStatus,Task 1 加。)

- [x] **Step 4: 适配 + 新增测试**

AuthApiTest:
- "注册并登录" 用例:注册现在需带 orgId,且注册出的是 PENDING 家长 → **不能直接登录**。改造:注册带 orgId → 断言注册成功(返回 id);但登录该家长应 400(待审核)。原"注册并登录拿 token"语义变了——拆成:① 注册家长成功;② 待审家长登录被拒(400)。
- 新增/保留:用 AuthTestSupport 造一个 ACTIVE 用户(它直接走 UserRepository.register ACTIVE,见计划三)能正常登录拿 token。
- 注:AuthTestSupport 造种子账号走 UserRepository.register,确保用 ACTIVE 状态(Task 1 register 默认/显式 ACTIVE)。实现者确认 AuthTestSupport 造的账号是 ACTIVE 可登录。

- [x] **Step 5: 全量回归 + 提交**

`./mvnw -q test`,全绿。
```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/user/ backend/src/test/java/com/sellm/auth/AuthApiTest.java && git commit -q -m "feat(rbac): 公开注册选机构+待审核;登录校验账号状态"
```

---

### Task 3: 机构端点 — 公开列表(注册选机构)+ 超管建机构/列表(TDD)

注册页要选机构,需一个**公开**(免登录)的机构列表;超管要能建机构、看所有机构。

**Files:**
- Modify: `backend/src/main/java/com/sellm/org/OrganizationRepository.java`(listAll)
- Modify: `backend/src/main/java/com/sellm/org/OrganizationMapper.java` + xml(findAll)
- Create: `backend/src/main/java/com/sellm/org/OrganizationController.java`
- Create: `backend/src/main/java/com/sellm/org/dto/{OrgResponse,CreateOrgRequest}.java`
- Modify: `backend/src/main/java/com/sellm/security/SecurityConfig.java`(GET /api/orgs/public permitAll;POST /api/orgs 限 SUPER_ADMIN)
- Test: `backend/src/test/java/com/sellm/org/OrganizationApiTest.java`

- [x] **Step 1: OrganizationMapper + Repository listAll**

Mapper 加 `List<Map<String,Object>> findAll();` + XML select(复用 orgMap,SELECT id,name,region ORDER BY id)。Repository 加 `List<Organization> listAll()`。

- [x] **Step 2: DTO**

`OrgResponse(id,name,region)`、`CreateOrgRequest(name,region)`。

- [x] **Step 3: OrganizationController**
```java
@RestController
@RequestMapping("/api/orgs")
public class OrganizationController {
    private final OrganizationRepository repo;
    private final CurrentUser currentUser;
    // 构造注入

    // 公开:注册选机构用(免登录)
    @GetMapping("/public")
    public Result<List<OrgResponse>> publicList() {
        // 返回 id+name(region 可含),供注册下拉
        ...
    }

    // 超管:建机构
    @PostMapping
    public Result<Long> create(@RequestBody CreateOrgRequest req) {
        // SecurityConfig 已限 SUPER_ADMIN;这里直接建
        Organization saved = repo.save(new Organization(null, req.getName(), req.getRegion()));
        return Result.ok(saved.getId());
    }

    // 超管:看所有机构
    @GetMapping
    public Result<List<OrgResponse>> listAll() {
        return Result.ok(...repo.listAll()...);
    }
}
```

- [x] **Step 4: SecurityConfig 规则**

加(放在 /api/** authenticated 之前):
```java
.requestMatchers(HttpMethod.GET, "/api/orgs/public").permitAll()
.requestMatchers(HttpMethod.POST, "/api/orgs").hasRole("SUPER_ADMIN")
.requestMatchers(HttpMethod.GET, "/api/orgs").hasRole("SUPER_ADMIN")
```
注:`/api/orgs/public` 必须 permitAll(注册时未登录)。注意顺序:specific 在前。

- [x] **Step 5: 测试 OrganizationApiTest**

- 公开列表免登录可访问(无 token GET /api/orgs/public → 200,含种子机构"阳光小学")。
- 超管建机构成功(200);非超管(MANAGER)POST /api/orgs → 403。
- 超管 GET /api/orgs 返回列表;MANAGER → 403。
用 AuthTestSupport 造 SUPER_ADMIN / MANAGER 账号(ACTIVE)取 token。

- [x] **Step 6: 全量回归 + 提交**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/org/ backend/src/main/resources/mybatis/OrganizationMapper.xml backend/src/main/java/com/sellm/security/SecurityConfig.java backend/src/test/java/com/sellm/org/OrganizationApiTest.java && git commit -q -m "feat(org): 公开机构列表 + 超管建机构/列表"
```

---

### Task 4: AccessGuard 支持 SUPER_ADMIN 跨机构(TDD)

**Files:**
- Modify: `backend/src/main/java/com/sellm/security/AccessGuard.java`
- Test: `backend/src/test/java/com/sellm/security/AccessGuardTest.java`(新建单测)

- [x] **Step 1: canAccess 加 SUPER_ADMIN 分支**

在 switch 加:
```java
            case SUPER_ADMIN:
                return true;  // 超管跨机构,可访问任意 child
```
(放在 MANAGER/TEACHER 分支前。)其余不变。

- [x] **Step 2: 单测 AccessGuardTest**

纯单元(new AccessGuard,构造 AuthPrincipal + Child):
- SUPER_ADMIN 访问任意机构 child → canAccess true。
- MANAGER 同机构 true、跨机构 false。
- TEACHER 同机构 true、跨机构 false。
- PARENT 自己监护 true、他人 false。
- child=null → false。

- [x] **Step 3: 全量回归 + 提交**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/security/AccessGuard.java backend/src/test/java/com/sellm/security/AccessGuardTest.java && git commit -q -m "feat(rbac): AccessGuard 支持 SUPER_ADMIN 跨机构访问"
```

---
### Task 5: 用户创建端点按角色分化(超管建MANAGER、机构管理者建TEACHER/PARENT,TDD)

改造 `POST /api/users` 的创建逻辑,按调用者角色限制可建角色与归属机构:
- **SUPER_ADMIN**:可为**指定机构**(请求带 orgId)创建 **MANAGER**(也可建其他角色到指定机构);新账号 ACTIVE。
- **MANAGER**:为**本机构**(orgId 取自己,忽略请求)创建 **TEACHER / PARENT**(**不可**建 MANAGER/SUPER_ADMIN);新账号 ACTIVE。
- TEACHER/PARENT:无权(端点级已拦,见 SecurityConfig)。

**Files:**
- Modify: `backend/src/main/java/com/sellm/user/dto/CreateUserRequest.java`(加 orgId — 仅超管用)
- Modify: `backend/src/main/java/com/sellm/user/UserManagementController.java`(按角色分化)
- Modify: `backend/src/main/java/com/sellm/security/SecurityConfig.java`(POST /api/users 限 SUPER_ADMIN + MANAGER)
- Test: `backend/src/test/java/com/sellm/user/UserManagementApiTest.java`(改造 + 新增角色矩阵)

- [x] **Step 1: CreateUserRequest 加 orgId**

加 `private Long orgId;`(超管创建时指定目标机构;MANAGER 创建时忽略此字段)。

- [x] **Step 2: UserManagementController.create 按角色分化**
```java
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
            // 机构管理者:只能建 TEACHER/PARENT,归属本机构
            if (targetRole != Role.TEACHER && targetRole != Role.PARENT) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "机构管理者只能创建老师或家长");
            }
            targetOrg = me.getOrgId();
        } else {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权创建账号");
        }
        AppUser saved = userRepository.register(
            req.getUsername(), req.getPassword(), targetRole, targetOrg, "ACTIVE");
        return Result.ok(saved.getId());
    }
```
注:上级创建的账号一律 ACTIVE(无需审核)。MANAGER 建 MANAGER 被业务逻辑拒(ACCESS_DENIED → 403)。

- [x] **Step 3: SecurityConfig POST /api/users 放开给 SUPER_ADMIN + MANAGER**

把原 `POST /api/users` 的 `hasRole("MANAGER")` 改为 `hasAnyRole("SUPER_ADMIN","MANAGER")`(具体角色能建什么由 controller 业务逻辑细分;端点级先挡掉 TEACHER/PARENT)。

- [x] **Step 4: 测试 UserManagementApiTest 角色矩阵**

改造既有 + 新增:
- 超管建 MANAGER 到机构 X 成功,新账号 orgId=X、role=MANAGER、status=ACTIVE(可查库或登录验证)。
- MANAGER 建 TEACHER 成功,orgId=建者机构、ACTIVE。
- MANAGER 建 PARENT 成功(ACTIVE,非 PENDING——上级建的直接可用)。
- **MANAGER 建 MANAGER → 403**(业务拒)。
- TEACHER 调 POST /api/users → 403(端点级)。
- PARENT 调 → 403。
- 注:既有"MANAGER 建 TEACHER"用例保留;"MANAGER 建 MANAGER"从原来可能允许 → 现在 403,需更新断言。

- [x] **Step 5: 全量回归 + 提交**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/user/ backend/src/main/java/com/sellm/security/SecurityConfig.java backend/src/test/java/com/sellm/user/UserManagementApiTest.java && git commit -q -m "feat(rbac): 用户创建按角色分化(超管建MANAGER/机构管理者建TEACHER/PARENT)"
```

---

### Task 6: 家长审核 + 全角色自助改密码(TDD)

机构管理者审核本机构待审家长(通过/拒绝);所有登录用户可改自己密码。

**Files:**
- Modify: `backend/src/main/java/com/sellm/user/AppUserMapper.java` + xml(findPendingByOrg、updateStatus、updatePassword)
- Modify: `backend/src/main/java/com/sellm/user/UserRepository.java`(listPendingByOrg、approve/reject(updateStatus)、changePassword)
- Modify: `backend/src/main/java/com/sellm/user/UserManagementController.java`(待审列表、审核、改密码端点)
- Create: `backend/src/main/java/com/sellm/user/dto/{ChangePasswordRequest,UserResponse}.java`
- Modify: `backend/src/main/java/com/sellm/security/SecurityConfig.java`(新端点授权)
- Test: `backend/src/test/java/com/sellm/user/UserAdminApiTest.java`

- [x] **Step 1: Mapper/Repository — 待审查询、改状态、改密码**

Mapper 加:`List<Map> findPendingByOrg(@Param Long orgId)`(WHERE org_id=? AND status='PENDING')、`updateStatus(@Param Long id, @Param String status)`、`updatePassword(@Param Long id, @Param String passwordHash)`。XML 对应实现(复用 userMap)。
Repository 加:`listPendingByOrg(Long orgId)`(返回 List<AppUser>)、`updateStatus(Long id, String status)`、`changePassword(Long userId, String rawPassword)`(内部 BCrypt encode 后 updatePassword)、`findById(Long id)`(审核时校验该家长确属本机构;若没有 findById 则加)。

- [x] **Step 2: DTO**

`ChangePasswordRequest(oldPassword,newPassword)`、`UserResponse(id,username,role,orgId,status)`。

- [x] **Step 3: UserManagementController 新端点**
```java
    // 机构管理者:本机构待审家长列表
    @GetMapping("/pending")
    public Result<List<UserResponse>> pendingParents() {
        AuthPrincipal me = currentUser.require();
        // 仅 MANAGER(端点级已限);返回本机构 PENDING 用户
        return Result.ok(map(userRepository.listPendingByOrg(me.getOrgId())));
    }

    // 审核通过/拒绝:PUT /api/users/{id}/approve | /reject
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        AuthPrincipal me = currentUser.require();
        AppUser target = userRepository.findById(id);
        // 校验:目标存在、是本机构、当前是 PENDING(防越权审他机构)
        if (target == null || !me.getOrgId().equals(target.getOrgId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权审核该账号");
        }
        userRepository.updateStatus(id, "ACTIVE");
        return Result.ok(null);
    }
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        // 同上校验;updateStatus(id,"REJECTED") 或直接删除——本计划用 REJECTED 状态(不可登录)
        ...
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
```
注:状态枚举值用字符串 ACTIVE/PENDING/REJECTED;登录校验 `!"ACTIVE".equals(status)` 已能挡 PENDING 和 REJECTED。审核端点强制校验目标属本机构(行级,防跨机构越权审核)。

- [x] **Step 4: SecurityConfig 授权**
```java
.requestMatchers(HttpMethod.GET, "/api/users/pending").hasRole("MANAGER")
.requestMatchers(HttpMethod.PUT, "/api/users/*/approve", "/api/users/*/reject").hasRole("MANAGER")
.requestMatchers(HttpMethod.PUT, "/api/users/me/password").authenticated()
```
(放在合适位置;`/api/users/me/password` 任何登录用户可调,改自己密码。注意 `/api/users/*/approve` 的通配符路径匹配。)

- [x] **Step 5: 测试 UserAdminApiTest**

- MANAGER 看本机构待审家长列表(先公开注册一个家长到该机构 → PENDING → MANAGER GET /api/users/pending 含它)。
- MANAGER approve 后,该家长能登录(status ACTIVE)。
- MANAGER reject 后,该家长仍不能登录。
- **跨机构越权**:机构B 的 MANAGER approve 机构A 的待审家长 → 403。
- 改密码:某 TEACHER 改自己密码成功,用新密码能登录、旧密码不能;原密码错 → 400。
- TEACHER 调 /api/users/pending → 403(仅 MANAGER)。

- [x] **Step 6: 全量回归 + 提交**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/user/ backend/src/main/resources/mybatis/AppUserMapper.xml backend/src/main/java/com/sellm/security/SecurityConfig.java backend/src/test/java/com/sellm/user/UserAdminApiTest.java && git commit -q -m "feat(rbac): 家长审核(通过/拒绝,行级)+ 全角色自助改密码"
```

---

### Task 7: 用户列表查询(超管看所有、机构管理者看本机构,TDD)

**Files:**
- Modify: `backend/src/main/java/com/sellm/user/AppUserMapper.java` + xml(findAll、findByOrg)
- Modify: `backend/src/main/java/com/sellm/user/UserRepository.java`(listAll、listByOrg)
- Modify: `backend/src/main/java/com/sellm/user/UserManagementController.java`(GET /api/users 按角色返回)
- Modify: `backend/src/main/java/com/sellm/security/SecurityConfig.java`(GET /api/users 限 SUPER_ADMIN+MANAGER)
- Test: `backend/src/test/java/com/sellm/user/UserListApiTest.java`

- [x] **Step 1: Mapper/Repository — findAll、findByOrg**

Mapper 加 `findAll()`、`findByOrg(@Param Long orgId)`(均返回 List<Map>,SELECT 不含 password_hash 或含但响应不暴露)。Repository 加 listAll/listByOrg 返回 List<AppUser>。

- [x] **Step 2: Controller GET /api/users 按角色**
```java
    @GetMapping
    public Result<List<UserResponse>> list() {
        AuthPrincipal me = currentUser.require();
        List<AppUser> users = (me.getRole() == Role.SUPER_ADMIN)
            ? userRepository.listAll()
            : userRepository.listByOrg(me.getOrgId());  // MANAGER 看本机构
        return Result.ok(map(users));  // UserResponse 不含 passwordHash
    }
```
**安全:UserResponse 绝不含 passwordHash**(只 id/username/role/orgId/status)。

- [x] **Step 3: SecurityConfig**
```java
.requestMatchers(HttpMethod.GET, "/api/users").hasAnyRole("SUPER_ADMIN","MANAGER")
```

- [x] **Step 4: 测试 UserListApiTest**

- 超管 GET /api/users 返回所有机构的用户(造跨机构若干用户验证含他机构的)。
- MANAGER GET /api/users 只返回本机构用户(不含他机构)。
- 响应不含 passwordHash(断言 JSON 无该字段)。
- TEACHER GET /api/users → 403。

- [x] **Step 5: 全量回归 + 提交**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/user/ backend/src/main/resources/mybatis/AppUserMapper.xml backend/src/main/java/com/sellm/security/SecurityConfig.java backend/src/test/java/com/sellm/user/UserListApiTest.java && git commit -q -m "feat(rbac): 用户列表(超管看全部/机构管理者看本机构,不暴露密码哈希)"
```

---
### Task 8: 前端 — 用户管理页按角色分化 + 注册选机构 + 改密码

前端对接新权限:用户管理页按当前角色渲染不同区块;家长注册页选机构;全角色改密码入口;SUPER_ADMIN 菜单。

**Files:**
- Modify: `frontend/src/api/users.js`(列表/建用户/待审/审核/改密码)
- Create: `frontend/src/api/orgs.js`(公开机构列表、超管建机构/列表)
- Modify: `frontend/src/stores/auth.js`(isSuperAdmin getter;ROLE_LABELS 加 SUPER_ADMIN="超级管理者")
- Modify: `frontend/src/views/UsersView.vue`(按角色分化)
- Create: `frontend/src/views/RegisterView.vue`(家长注册:选机构)
- Modify: `frontend/src/views/LoginView.vue`(加"家长注册"入口链接)
- Modify: `frontend/src/router/index.js`(/register 公开路由)

- [x] **Step 1: API 模块**

`users.js` 增/改:
```js
export const listUsers = () => http.get('/users')
export const createUser = (payload) => http.post('/users', payload)
export const listPendingParents = () => http.get('/users/pending')
export const approveUser = (id) => http.put(`/users/${id}/approve`)
export const rejectUser = (id) => http.put(`/users/${id}/reject`)
export const changeMyPassword = (oldPassword, newPassword) =>
  http.put('/users/me/password', { oldPassword, newPassword })
```
`orgs.js`:
```js
import http from './http'
export const publicOrgs = () => http.get('/orgs/public')
export const listOrgs = () => http.get('/orgs')
export const createOrg = (payload) => http.post('/orgs', payload)
```

- [x] **Step 2: auth store**

ROLE_LABELS 加 `SUPER_ADMIN: '超级管理者'`;加 getter `isSuperAdmin: (s) => s.role === 'SUPER_ADMIN'`。

- [x] **Step 3: UsersView 按角色分化**

页面按 `auth.role` 渲染:
- **SUPER_ADMIN**:机构管理区(建机构 + 机构列表)+ 建 MANAGER(选目标机构 + 用户名密码)+ 全部用户列表(listUsers)。
- **MANAGER**:建用户区(角色仅 TEACHER/PARENT 可选,orgId 不填——后端取本机构)+ 待审家长列表(listPendingParents,每行通过/拒绝按钮)+ 本机构用户列表。
- **TEACHER/PARENT**:只显示"修改密码"区(旧密码+新密码 → changeMyPassword)。
- **所有角色**页面顶部都有"修改密码"区(全员可改自己密码)。
用 `v-if="auth.isSuperAdmin"` / `v-else-if="auth.isManager"` 分区;改密码区无条件显示。
角色下拉:MANAGER 的建用户只列 TEACHER/PARENT 选项(去掉 MANAGER,因为后端会拒);SUPER_ADMIN 的建 MANAGER 区固定建 MANAGER。

- [x] **Step 4: RegisterView(家长注册选机构)**

新建公开页:用户名/密码 + **机构下拉(publicOrgs 加载)** + 提交 register({username,password,orgId})。提交成功提示"注册成功,待机构管理者审核后可登录",跳回 /login。

- [x] **Step 5: LoginView 加注册入口 + 路由**

LoginView 加一个"家长注册"链接 → /register。router 加 `{ path: '/register', component: () => import('../views/RegisterView.vue') }`(公开,守卫放行——守卫里 /login 和 /register 都不拦)。

- [x] **Step 6: 路由守卫放行 /register**

router beforeEach:`if (to.path !== '/login' && to.path !== '/register' && !auth.isLoggedIn) return '/login'`。

- [x] **Step 7: 菜单**

MainLayout 用户管理菜单项:从 `v-if="auth.isManager"` 改为 `v-if="auth.isManager || auth.isSuperAdmin"`(超管也要进用户管理)。

- [x] **Step 8: 构建验证 + 提交**

Run: `cd "D:/works/test/SELLM/frontend" && npm run build`,Expected 成功。
```bash
cd "D:/works/test/SELLM" && git add frontend/src/ && git commit -q -m "feat(frontend): 用户管理按角色分化 + 家长注册选机构 + 自助改密码"
```

---

### Task 9: 端到端联调验证(四级权限全流程)

起 dev 后端 + 前端,curl 走四级权限关键路径,验证升级后契约连通与权限正确。

- [x] **Step 1: 起 dev 后端(后台)**

`cd "D:/works/test/SELLM/backend" && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`(run_in_background)。
**注意**:dev H2 文件库里旧 admin 是 MANAGER。若 DevSeeder 改成幂等纠正(Task 1 推荐),admin 会被更新为 SUPER_ADMIN;若不是,需先删 `backend/data/` 再启动让种子重建。实现者按 Task 1 的实际做法处理,确保 admin 登录后是 SUPER_ADMIN。

- [x] **Step 2: curl 验证四级权限关键路径**
```bash
BASE=http://localhost:8080
# 1. 超管登录(admin),role 应 SUPER_ADMIN
SA=$(curl -s -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}')
echo "SUPER_ADMIN LOGIN: $SA"   # 期望 role:"SUPER_ADMIN"
SA_TOKEN=...(提取)
# 2. 超管建机构
curl -s -X POST $BASE/api/orgs -H "Authorization: Bearer $SA_TOKEN" -H "Content-Type: application/json" -d '{"name":"星星康复中心","region":"南京"}'; echo
# 3. 超管为机构1建一个 MANAGER
curl -s -X POST $BASE/api/users -H "Authorization: Bearer $SA_TOKEN" -H "Content-Type: application/json" -d '{"username":"mgr1","password":"mgr123","role":"MANAGER","orgId":1}'; echo
# 4. 该 MANAGER 登录,建一个 TEACHER
MGR=$(curl -s -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"mgr1","password":"mgr123"}'); echo "MGR LOGIN: $MGR"
MGR_TOKEN=...(提取)
curl -s -X POST $BASE/api/users -H "Authorization: Bearer $MGR_TOKEN" -H "Content-Type: application/json" -d '{"username":"teacher1","password":"tea123","role":"TEACHER"}'; echo
# 5. MANAGER 建 MANAGER → 应 403
curl -s -o /dev/null -w "MGR builds MGR status=%{http_code}\n" -X POST $BASE/api/users -H "Authorization: Bearer $MGR_TOKEN" -H "Content-Type: application/json" -d '{"username":"mgr2","password":"x","role":"MANAGER"}'
# 6. 公开机构列表(免登录)
curl -s $BASE/api/orgs/public; echo
# 7. 家长公开注册(选机构1)→ PENDING
curl -s -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d '{"username":"parent1","password":"par123","orgId":1}'; echo
# 8. 待审家长登录 → 应 400(待审核)
curl -s -o /dev/null -w "pending login status=%{http_code}\n" -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"parent1","password":"par123"}'
# 9. MANAGER 看待审家长列表
curl -s $BASE/api/users/pending -H "Authorization: Bearer $MGR_TOKEN"; echo
# 10. MANAGER 审核通过 parent1(取其 id)→ 之后 parent1 能登录
# (提取 pending 列表里 parent1 的 id,PUT approve,再登录验证 200)
# 11. teacher1 改自己密码,新密码登录成功
```
**预期**:超管 role SUPER_ADMIN;建机构/建 MANAGER 成功;MANAGER 建 TEACHER 成功、建 MANAGER 403;公开机构列表免登录可见;家长注册 PENDING 不能登录;审核通过后能登录;改密码生效。逐条核对记录。

- [x] **Step 3: 浏览器人工验收清单(记录)**

admin 登录(超管)→ 用户管理见"建机构/建管理者/全部用户";建机构、建 mgr1 → 退出、mgr1 登录 → 用户管理见"建老师家长/待审家长/本机构用户";建 teacher1;另开无痕用 /register 注册家长选机构 → mgr1 审核 → 家长登录;teacher1 登录只见"改密码"。

- [x] **Step 4: 停服务 + 提交联调记录**

停后端/前端(TaskStop + 必要时 taskkill 端口),释放 8080/5173。更新 `frontend/INTEGRATION.md` 追加四级权限联调结果。
```bash
cd "D:/works/test/SELLM" && git add frontend/INTEGRATION.md && git commit -q -m "docs: 四级权限体系端到端联调结果" --allow-empty
```

---

## 后续计划(不在本计划范围)

1. **真实 AI 接入**:MockAiModel 换合规大模型 API(下一计划,你最初定的第二步)。
2. 量表动态化、记录列表分页、机构编辑/停用、用户停用/重置密码(管理员代重置)、家长与孩子的绑定交互。
3. 审计日志:谁审核了谁、谁建了谁(合规追溯)。
4. 小程序家长端;生产部署(docker-compose)。

---

## 自检结论

- **范围覆盖**:四级角色(SUPER_ADMIN 新增)、app_user.status + 登录校验、公开注册选机构+待审、机构端点(公开列表+超管建/列表)、AccessGuard 超管跨机构、用户创建按角色分化(超管建MANAGER/机构管理者建TEACHER/PARENT)、家长审核(通过/拒绝+行级)、全角色自助改密码、用户列表(超管全部/机构管理者本机构,不暴露密码)、前端按角色分化的用户管理页+注册选机构。覆盖你描述的完整权限矩阵。
- **红线**:① 端点级 RBAC + 行级 AccessGuard 双层;② 创建账号 orgId/status 后端强制(MANAGER 建的归本机构、上级建的 ACTIVE、公开注册 PENDING);③ 审核强制校验目标属本机构(防跨机构越权审核);④ 改密码只能改自己(用 me.getUserId());⑤ 用户列表响应不含 passwordHash;⑥ 待审/拒绝账号不能登录。
- **兼容性**:Role 加枚举值、register 加 status 参数(保留旧签名委托)、CreateUserRequest/RegisterRequest 加字段——均向后兼容,既有测试通过适配保持绿。dev admin 升级处理已在 Task 1 说明(幂等纠正或重建库)。
- **占位符**:无 TBD;Controller 代码块中 `...` 处(如 reject 实现、map 辅助)已用文字说明预期行为,实现者据此补全(reject=updateStatus REJECTED;map=AppUser→UserResponse 不含密码)。
- **测试策略**:每个后端任务 H2+MockMvc 测角色矩阵与行级越权(尤其跨机构审核 403、MANAGER 建 MANAGER 403、待审登录 400、密码不暴露);前端靠 build;Task 9 真实 dev 联调四级全流程。
