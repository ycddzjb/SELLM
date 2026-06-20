# 阶段 C — 家长注册改造 + 老师审核 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 家长公开注册从「用户名/密码/机构」扩展为「姓名 + 账号 + 密码 + 儿童姓名 + 残障类型 + 所在学校(机构)+ 所在班级的老师/康复师」;审核者从机构管理员改为**家长注册时所选的那位老师**;审核通过时由系统创建儿童档案并关联监护家长。

**Architecture:** 延续模块化单体。新增 `parent_profile` 表(暂存家长扩展信息 + 待建儿童信息,PII 加密),不污染 `app_user`。注册写 app_user(PENDING)+ parent_profile;审核(TEACHER 行级:仅本人被指派的)通过时创建 Child(guardian 指向家长)+ 置 app_user ACTIVE。管理员"本机构家长列表"升级展示完整字段。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / MyBatis / H2(test) / Vue 3 + Element Plus。根包 `com.sellm`。

**Source spec:** `docs/superpowers/specs/2026-06-19-asd-assistant-v2-requirements.md` 第 3.2、3.3、4 节

---

## 设计决策(已与用户确认)

1. **家长信息存储**:新建 `parent_profile` 表,字段 `user_id`(PK,= app_user.id)、`name_enc`(姓名,加密)、`relationship`(亲戚关系:MOTHER_SON/MOTHER_DAUGHTER/FATHER_SON/FATHER_DAUGHTER)、`assigned_teacher_id`(注册所选老师,审核归属)、`child_name_enc`(待建儿童姓名,加密)、`child_disorder_type`、`class_id`(所在班级)、`child_id`(审核通过后回填)。不污染 app_user。
2. **儿童档案创建时机**:审核**通过时**创建 Child(guardian_user_id=家长,org_id=家长机构,name/disorderType 取注册暂存),并回填 parent_profile.child_id。未通过不产生儿童档案。
3. **审核权限**:行级——仅 `parent_profile.assigned_teacher_id == 当前老师` 才能审核该家长(端点级限 TEACHER,行级在 service 校验)。
4. **亲戚关系**:枚举 `Relationship`(MOTHER_SON 母子 / MOTHER_DAUGHTER 母女 / FATHER_SON 父子 / FATHER_DAUGHTER 父女),带中文标签。
5. **PII 加密**:家长姓名、儿童姓名经 FieldCipher 加密落库(复用 child 加密红线),出网在 Response 层解密给授权角色。
6. **班级老师选择**:注册页需公开端点拉「某机构的班级 + 班级下老师」供选择(免登录);新增公开只读端点。

---

## 与现状的关键变更

- `RegisterRequest` 扩字段:name、relationship、childName、childDisorderType、orgId(已有)、assignedTeacherId、classId
- `AuthController.register`:写 app_user(PENDING)后写 parent_profile;事务原子
- 审核流转:`UserManagementController` 的 approve/reject 从 MANAGER 改 TEACHER + 行级校验;通过时建 Child
- 新增公开端点:`GET /api/orgs/public/{orgId}/classes`(班级列表)、`GET /api/classes/{classId}/teachers`(班级下老师),供注册页选择
- `ParentResponse`(阶段 A 占位)升级:含姓名/儿童姓名/关系/班级名(管理员看本机构家长完整信息)
- SecurityConfig:approve/reject 改 TEACHER;新增公开端点 permitAll
- 前端 RegisterView 扩表单;UsersView 管理员家长列表展示完整字段;老师端新增"待审核家长"区(从管理员迁移)

---

## 文件结构(本计划范围)

```
backend/src/main/
  resources/
    schema.sql                          # parent_profile 表
    seed-dev.sql                        # (可选)示例班级/老师便于联调
    mybatis/ParentProfileMapper.xml     # 新增
  java/com/sellm/
    common/Relationship.java            # 亲戚关系枚举(Task 1)
    parent/                             # 新增家长档案模块
      ParentProfile.java
      ParentProfileMapper.java
      ParentProfileRepository.java
    user/
      dto/RegisterRequest.java          # 扩字段(Task 3)
      AuthController.java               # 注册写 profile(Task 3)
      UserManagementController.java     # 审核改老师 + 通过建儿童(Task 4)
      dto/ParentResponse.java           # 升级完整字段(Task 5)
    clazz/
      ClazzController.java              # 公开:班级下老师端点(Task 2)
    org/
      OrganizationController.java       # 公开:机构班级端点(Task 2)
    security/SecurityConfig.java        # 授权调整(Task 3/4)
backend/src/test/java/com/sellm/
    common/RelationshipTest.java
    parent/ParentProfileRepositoryTest.java
    auth/ParentRegisterApiTest.java
    user/ParentApprovalApiTest.java     # 老师审核 + 通过建儿童 + 行级
    user/ParentListApiTest.java         # 升级断言
frontend/src/
    api/auth.js / orgs.js / classes.js  # 公开端点
    api/meta.js                         # 加 RELATIONSHIPS 枚举
    views/RegisterView.vue              # 扩表单(联动:机构→班级→老师)
    views/UsersView.vue                 # 管理员家长完整列表 + 老师待审区
    layouts/MainLayout.vue              # (老师已含用户管理,无需改菜单)
```

---

### Task 1: Relationship 枚举 + parent_profile schema(TDD)

亲戚关系枚举;parent_profile 表。

**Files:** common/Relationship.java, schema.sql, RelationshipTest.java

- [ ] **Step 1:** `Relationship` 枚举:MOTHER_SON("母子")、MOTHER_DAUGHTER("母女")、FATHER_SON("父子")、FATHER_DAUGHTER("父女");getLabel() + valueOf 校验工具(参照 DisorderType 范式)
- [ ] **Step 2:** schema.sql 追加:
```sql
CREATE TABLE IF NOT EXISTS parent_profile (
    user_id              BIGINT PRIMARY KEY,
    name_enc             VARCHAR(512),
    relationship         VARCHAR(32),
    assigned_teacher_id  BIGINT,
    child_name_enc       VARCHAR(512),
    child_disorder_type  VARCHAR(64),
    class_id             BIGINT,
    child_id             BIGINT,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```
- [ ] **Step 3:** RelationshipTest(枚举齐全 + 标签 + 非法码抛异常)
- [ ] **Step 4:** 全量回归绿 → Commit

---

### Task 2: 公开端点 — 机构班级 + 班级下老师(TDD)

注册页选「所在学校→班级→班级老师」需免登录数据。

**Files:** ClazzController, OrganizationController(或新建只读端点),TeacherClassMapper(已有 findClassIdsByTeacher,需反查),SecurityConfig,测试

- [ ] **Step 1:** `GET /api/orgs/public/{orgId}/classes` → 该机构班级列表(id+name);复用 ClazzRepository.listByOrg
- [ ] **Step 2:** `GET /api/classes/public/{classId}/teachers` → 该班级关联老师(userId + username);TeacherClassMapper 加 `findTeacherIdsByClass(classId)`,再查 app_user 取 username(只返回 id+username,不含敏感字段)
- [ ] **Step 3:** SecurityConfig:这两个 public 端点 permitAll(放在 /api/classes、/api/orgs 既有规则之前)
- [ ] **Step 4:** 测试:建机构+班级+老师绑班级 → 公开查班级含它、查班级老师含该老师(免 token)
- [ ] **Step 5:** 全量回归绿 → Commit

---

### Task 3: 家长注册扩字段 + parent_profile 落库(TDD)

注册写 app_user(PENDING)+ parent_profile(加密 PII);事务原子。

**Files:** parent/ParentProfile*.java, ParentProfileMapper.xml, RegisterRequest, AuthController, ParentRegisterApiTest

- [ ] **Step 1:** ParentProfile 实体 + Mapper(insert/findByUserId/updateChildId/findByOrg 关联查)+ XML;ParentProfileRepository(save 加密 name/childName,findByUserId 解密)
- [ ] **Step 2:** RegisterRequest 扩:name、relationship、childName、childDisorderType、assignedTeacherId、classId(orgId 已有)
- [ ] **Step 3:** AuthController.register 改造(@Transactional):
  - 校验 orgId、assignedTeacherId 非空;校验 relationship/childDisorderType 合法码
  - 校验 assignedTeacherId 是 TEACHER 且属 orgId(防乱指派);classId 属 orgId
  - register app_user(PARENT, orgId, PENDING)→ 拿 userId
  - parentProfileRepository.save(userId, name, relationship, assignedTeacherId, childName, childDisorderType, classId)
- [ ] **Step 4:** ParentProfileRepositoryTest + ParentRegisterApiTest(注册成功落两表 / 缺老师 400 / 非法关系码 400 / 指派非本机构老师 400)
- [ ] **Step 5:** 全量回归绿 → Commit

---

### Task 4: 审核改老师 + 通过建儿童(TDD)

approve/reject 从 MANAGER 改 TEACHER + 行级(仅 assigned_teacher_id 本人);通过时建 Child 关联家长。

**Files:** UserManagementController, ChildRepository(已有 save), SecurityConfig, ParentApprovalApiTest

- [ ] **Step 1:** SecurityConfig:`PUT /api/users/*/approve|reject` 从 hasRole("MANAGER") 改 hasRole("TEACHER");`GET /api/users/pending` 同步改 TEACHER(老师看分派给自己的待审)
- [ ] **Step 2:** pending 列表改造:老师只看 `assigned_teacher_id == me` 的 PENDING 家长(ParentProfileRepository.findPendingByTeacher 关联 app_user.status=PENDING)
- [ ] **Step 3:** approve 改造(@Transactional):
  - 校验目标是 PENDING 家长且 parent_profile.assigned_teacher_id == me.userId(否则 ACCESS_DENIED)
  - 创建 Child(name=child_name 解密, disorderType=child_disorder_type, orgId=家长 orgId, guardianUserId=家长 userId)
  - 回填 parent_profile.child_id;app_user 置 ACTIVE
- [ ] **Step 4:** reject 改造:行级同上(assigned_teacher_id==me);置 REJECTED;不建儿童
- [ ] **Step 5:** ParentApprovalApiTest:
  - 被指派老师 approve → 200;app_user ACTIVE;Child 已建(guardian=家长, org=家长机构);parent_profile.child_id 回填
  - 非指派老师 approve → 403(行级)
  - MANAGER approve → 403(端点级已改 TEACHER)
  - reject → REJECTED 且无 Child
  - 家长审核通过后能登录(status ACTIVE)
- [ ] **Step 6:** 全量回归绿 → Commit

---

### Task 5: 管理员家长列表升级完整字段(TDD)

ParentResponse 含姓名/儿童姓名/关系标签/班级名(管理员看本机构家长)。

**Files:** ParentResponse, UserManagementController(/parents), ParentProfileRepository(findByOrg join), ParentListApiTest(升级)

- [ ] **Step 1:** ParentProfileRepository 加 `listByOrg(orgId)`:join app_user(role=PARENT, org)+ parent_profile + class_room(班级名)+ child;解密姓名/儿童姓名
- [ ] **Step 2:** ParentResponse 扩:id/username/status + name/relationship(标签)/childName/className
- [ ] **Step 3:** `GET /api/users/parents`(MANAGER)改为返回完整字段(无 profile 的旧家长字段留空,兼容)
- [ ] **Step 4:** ParentListApiTest 升级:注册一个家长(带 profile)→ 管理员看到完整字段;他机构家长不可见
- [ ] **Step 5:** 全量回归绿 → Commit

---

### Task 6: 前端 — 注册页扩表单 + 老师待审区 + 管理员家长完整列表

**Files:** api/auth.js、orgs.js、classes.js、meta.js,RegisterView.vue,UsersView.vue

- [ ] **Step 1:** meta.js 加 `RELATIONSHIPS`(code+label);api 加公开端点 listPublicClasses(orgId)、listClassTeachers(classId)
- [ ] **Step 2:** RegisterView 扩表单:姓名、账号、密码、儿童姓名、残障类型(单选 DISORDER_TYPES)、所在学校(机构,已有)、**联动**:选机构→拉班级→选班级→拉老师→选老师;亲戚关系(RELATIONSHIPS)。提交全字段
- [ ] **Step 3:** UsersView 老师区(auth.isTeacher):新增"待审核家长"卡(GET /users/pending,展示姓名/儿童/关系)+ 通过/拒绝按钮(老师只看分派给自己的)
- [ ] **Step 4:** UsersView 管理员区"本机构家长"表升级列:姓名、儿童姓名、关系、班级、状态
- [ ] **Step 5:** (管理员区原"待审核家长"卡移除——审核已归老师)
- [ ] **Step 6:** `npm run build` 通过 → Commit

---

### Task 7: 端到端联调

起 dev 后端 + curl:超管建机构+管理员→管理员建老师绑班级→公开查班级/班级老师→家长注册(指派该老师)→该老师看待审(含此家长)→老师审核通过→验证 Child 已建+家长可登录→非指派老师/管理员审核 403。记录 INTEGRATION.md。

- [ ] **Step 1:** 起 dev 后端(schema 新增 parent_profile,CREATE IF NOT EXISTS 兼容;必要时删 data/ 重建)
- [ ] **Step 2:** curl 链路(8~10 条):公开班级/老师查询、注册落两表、老师行级待审、通过建儿童、家长登录、越权 403
- [ ] **Step 3:** 停服务,追加 INTEGRATION.md,提交

---

## 验证清单

1. **后端全量回归** `./mvnw test` 全绿(现 155 + 新增约 15-18)
2. **前端 build** `npm run build` 无错误
3. **端到端 curl** 全通
4. **红线**:家长/儿童姓名加密落库;审核行级(仅指派老师);通过才建儿童;PENDING 不能登录(已有)
5. **兼容**:旧无 profile 家长(阶段 A 造的)列表字段留空不报错;Child/AccessGuard 行级不变

---

## 后续(不在本计划)

- 阶段 D:儿童档案大扩展(基线/历史/复盘/提醒)
- 阶段 E:真实大模型接入(评估报告 / IEP / 家庭 IEP 生成)
