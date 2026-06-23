# ASD 助手 — 计划六:组织模型 + 班级(路线图阶段 A)Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 升级组织模型为后续所有功能打地基:障碍类型枚举、机构扩字段(障碍类型多选/省/市)、超管一体创建机构+机构管理员、新增班级实体、老师绑定班级、机构管理员菜单改造(儿童档案/班级管理/用户管理)与能力(维护老师选班级、查看本机构家长)。

**Architecture:** 模块化单体(Spring Boot 3.2.5 + MyBatis + JWT)+ Vue3 管理端。复用计划一~五(master)。新增 `class`(班级)模块、`teacher_class` 关联;机构/班级障碍类型存逗号分隔字符串;DisorderType 统一枚举。行级权限延续:超管跨机构、管理员/老师限本机构。测试 H2 + MockMvc;前端 build + dev 联调。

**Tech Stack:** Java 17、Spring Boot 3.2.5、MyBatis、H2(test)、Vue 3 + Element Plus。根包 `com.sellm`。

**Source spec:** `docs/superpowers/specs/2026-06-19-asd-assistant-v2-requirements.md`(第 2、3 节)

---

## 设计决策(已与用户确认)

1. **DisorderType 统一枚举**(8 品类):ASD、DEVELOPMENTAL_DELAY、INTELLECTUAL、LANGUAGE、SENSORY_INTEGRATION、CEREBRAL_PALSY、ADHD、HEARING_VISION。各带中文标签(前端展示)。
2. **障碍类型多选存储**:逗号分隔字符串(如 `"ASD,ADHD"`)存于 organization.disorder_types、class.disorder_types。
3. **机构一体创建**:超管一个请求原子创建 机构 + 该机构 MANAGER(管理员 orgId 自动指向新机构);上级建的账号 ACTIVE。
4. **班级新增实体**:id/name/org_id/disorder_types;机构管理员维护本机构班级。
5. **老师绑班级**:teacher_class 多对多关联表;管理员建老师时多选班级。
6. **管理员菜单**:儿童档案 / 班级管理 / 用户管理(去掉评估/报告/IEP)。
7. **家长字段**:完整家长字段(姓名/儿童/关系/班级)依赖阶段 C;本计划管理员"查看家长"先展示现有字段(username/role/status),框架先就位。

---

## 与现状的关键变更

- `organization` 表加 disorder_types/province/city 列;`Organization` 实体、Mapper、Repository 同步。
- `OrganizationController` POST 改为一体创建(建机构 + MANAGER);需注入 UserRepository。
- 新增 `com.sellm.clazz` 包(避开 Java 关键字 class;包名用 clazz)+ class 表 + teacher_class 表。
- UserManagementController 建老师支持 classIds(多选);新增"本机构家长列表"端点。
- 前端 MainLayout 菜单按角色渲染(管理员= 儿童档案/班级管理/用户管理);新增班级管理页;UsersView 管理员区加"维护老师(选班级)+ 家长列表"。
- DevSeeder/seed:机构种子补 disorder_types/province/city;可加一个示例班级。

---

## 文件结构(本计划范围)

```
backend/src/main/
  resources/
    schema.sql                      # organization 加列;新增 class、teacher_class 表
    seed-dev.sql                    # 机构种子补字段 + 示例班级
  java/com/sellm/
    common/DisorderType.java        # 障碍类型枚举(Task 1)
    org/
      Organization.java             # 加 disorderTypes/province/city(Task 2)
      OrganizationMapper(.xml) / OrganizationRepository
      OrganizationController.java   # 一体创建机构+管理员(Task 3)
      dto/CreateOrgRequest(扩) / OrgResponse(扩)
    clazz/
      Clazz.java / ClazzMapper(.xml) / ClazzRepository
      ClazzController.java          # 班级 CRUD(本机构)(Task 4)
      dto/ClazzRequest / ClazzResponse
    user/
      UserManagementController.java # 建老师选班级 + 本机构家长列表(Task 5)
      TeacherClassMapper(.xml) / 关联读写
      dto/CreateUserRequest(加 classIds) / TeacherResponse / ParentResponse
    security/SecurityConfig.java    # /api/classes、家长列表端点授权
frontend/src/
  api/orgs.js(扩) / classes.js(新) / users.js(扩) / meta.js(障碍类型枚举,新)
  views/ClassesView.vue(新) / UsersView.vue(扩管理员区) / 机构建表单扩字段
  layouts/MainLayout.vue           # 菜单按角色(管理员三项)
  router/index.js                  # /classes 路由
```

---

### Task 1: DisorderType 障碍类型枚举(TDD)

8 品类障碍类型统一枚举,带中文标签;提供"逗号串 ↔ 列表"与校验工具,供机构/班级多选存储复用。

**Files:**
- Create: `backend/src/main/java/com/sellm/common/DisorderType.java`
- Test: `backend/src/test/java/com/sellm/common/DisorderTypeTest.java`

- [x] **Step 1: 写枚举**

`DisorderType.java`:
```java
package com.sellm.common;

import java.util.ArrayList;
import java.util.List;

public enum DisorderType {
    ASD("孤独症"),
    DEVELOPMENTAL_DELAY("发育迟缓"),
    INTELLECTUAL("智力障碍"),
    LANGUAGE("语言障碍"),
    SENSORY_INTEGRATION("感统失调"),
    CEREBRAL_PALSY("脑瘫"),
    ADHD("注意缺陷多动障碍"),
    HEARING_VISION("听视障");

    private final String label;

    DisorderType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 校验逗号分隔串里每个码都是合法枚举名;非法抛 IllegalArgumentException。空串/ null 视为合法(无类型)。 */
    public static void validateCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String code : csv.split(",")) {
            String c = code.trim();
            if (c.isEmpty()) continue;
            DisorderType.valueOf(c); // 非法码抛 IllegalArgumentException
        }
    }

    /** 逗号串转枚举列表(忽略空白项);非法码抛异常。 */
    public static List<DisorderType> fromCsv(String csv) {
        List<DisorderType> list = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return list;
        }
        for (String code : csv.split(",")) {
            String c = code.trim();
            if (!c.isEmpty()) {
                list.add(DisorderType.valueOf(c));
            }
        }
        return list;
    }
}
```

- [x] **Step 2: 写测试**

`DisorderTypeTest.java`:
```java
package com.sellm.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DisorderTypeTest {

    @Test
    void 八个品类齐全且有中文标签() {
        assertThat(DisorderType.values()).hasSize(8);
        assertThat(DisorderType.ASD.getLabel()).isEqualTo("孤独症");
        assertThat(DisorderType.ADHD.getLabel()).isEqualTo("注意缺陷多动障碍");
    }

    @Test
    void 合法逗号串校验通过() {
        DisorderType.validateCsv("ASD,ADHD");
        DisorderType.validateCsv("");
        DisorderType.validateCsv(null);
    }

    @Test
    void 非法码校验抛异常() {
        assertThatThrownBy(() -> DisorderType.validateCsv("ASD,NOPE"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 逗号串转列表() {
        assertThat(DisorderType.fromCsv("ASD,ADHD")).containsExactly(DisorderType.ASD, DisorderType.ADHD);
        assertThat(DisorderType.fromCsv(" ASD , LANGUAGE ")).containsExactly(DisorderType.ASD, DisorderType.LANGUAGE);
        assertThat(DisorderType.fromCsv("")).isEmpty();
    }
}
```

- [x] **Step 3: 跑测试 + 全量回归**

`cd "D:/works/test/SELLM/backend" && ./mvnw -q test -Dtest=DisorderTypeTest`(4 PASS),再 `./mvnw -q test` 全绿(此前 122 + 4)。报告实际数。

- [x] **Step 4: Commit**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/common/DisorderType.java backend/src/test/java/com/sellm/common/DisorderTypeTest.java && git commit -q -m "feat(org): 障碍类型 DisorderType 枚举(8品类+中文标签+csv工具)"
```

---

### Task 2: 机构扩字段 disorderTypes/province/city(TDD)

organization 表与实体加 disorder_types(逗号串)、province、city;Repository 读写;校验障碍类型码合法。

**Files:**
- Modify: `backend/src/main/resources/schema.sql`(organization 加 3 列)
- Modify: `backend/src/main/java/com/sellm/org/Organization.java`(加 3 字段)
- Modify: `backend/src/main/java/com/sellm/org/OrganizationMapper.java`(不变,用 map)+ `mybatis/OrganizationMapper.xml`(resultMap/insert/select 加 3 列)
- Modify: `backend/src/main/java/com/sellm/org/OrganizationRepository.java`(save/findById/listAll 带新字段)
- Test: `backend/src/test/java/com/sellm/org/OrganizationRepositoryTest.java`(适配 + 新字段断言)

- [x] **Step 1: schema 加列**

organization 的 CREATE TABLE 之后追加(ALTER IF NOT EXISTS,兼容已存在 dev 库):
```sql
ALTER TABLE organization ADD COLUMN IF NOT EXISTS disorder_types VARCHAR(256);
ALTER TABLE organization ADD COLUMN IF NOT EXISTS province VARCHAR(64);
ALTER TABLE organization ADD COLUMN IF NOT EXISTS city VARCHAR(64);
```

- [x] **Step 2: Organization 实体加字段**

加 `disorderTypes`(String,逗号串)、`province`、`city` + getter/setter。**保留原 3 参构造器**(委托新构造器,新字段 null);加 6 参全参构造器(id,name,region,disorderTypes,province,city)。确保计划三/五既有调用不破。

- [x] **Step 3: Mapper XML 加列**

orgMap 加 disorder_types→disorderTypes、province、city;insert 加这 3 列与占位符;findById/findAll/listAll 的 SELECT 加这 3 列。

- [x] **Step 4: Repository 带新字段**

save 的 row 加 disorderTypes/province/city;组装 Organization(findById/listAll)带新字段。save 前调 `DisorderType.validateCsv(org.getDisorderTypes())` 校验(非法码 → IllegalArgumentException → 全局 advice 400)。

- [x] **Step 5: 适配测试**

OrganizationRepositoryTest:既有用例保持(3 参构造或 save 仍可用);新增:save 带 disorderTypes="ASD,ADHD"/province/city → findById 读回一致;listAll 含新字段。

- [x] **Step 6: 全量回归 + 提交**

`./mvnw -q test` 全绿。
```bash
cd "D:/works/test/SELLM" && git add backend/src/main/resources/schema.sql backend/src/main/java/com/sellm/org/ backend/src/main/resources/mybatis/OrganizationMapper.xml backend/src/test/java/com/sellm/org/OrganizationRepositoryTest.java && git commit -q -m "feat(org): 机构扩字段 障碍类型(多选)/省/市"
```

---
### Task 3: 超管一体创建机构 + 机构管理员(TDD)

超管一个请求原子创建:机构(含障碍类型/省/市)+ 该机构的 MANAGER 账号(orgId 自动指向新机构,ACTIVE)。

**Files:**
- Modify: `backend/src/main/java/com/sellm/org/dto/CreateOrgRequest.java`(加 disorderTypes/province/city + managerUsername/managerPassword/managerName?)
- Modify: `backend/src/main/java/com/sellm/org/dto/OrgResponse.java`(加 disorderTypes/province/city)
- Modify: `backend/src/main/java/com/sellm/org/OrganizationController.java`(POST 一体创建,注入 UserRepository)
- Test: `backend/src/test/java/com/sellm/org/OrganizationApiTest.java`(适配 + 一体创建用例)

- [x] **Step 1: DTO 扩字段**

`CreateOrgRequest`:name、region(可选)、disorderTypes(逗号串)、province、city、**managerUsername、managerPassword**(建该机构管理员;managerName 可选,本计划 AppUser 暂无 name 字段,留 C,先不强求)。
`OrgResponse`:加 disorderTypes/province/city。

- [x] **Step 2: Controller 一体创建**

`OrganizationController` 注入 `UserRepository`。POST:
```java
    @PostMapping
    public Result<Long> create(@RequestBody CreateOrgRequest req) {
        if (req.getManagerUsername() == null || req.getManagerPassword() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请填写机构管理员账号密码");
        }
        if (userRepository.findByUsername(req.getManagerUsername()) != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "管理员用户名已存在");
        }
        Organization org = repo.save(new Organization(
            null, req.getName(), req.getRegion(),
            req.getDisorderTypes(), req.getProvince(), req.getCity()));
        // 一体创建该机构 MANAGER(ACTIVE)
        userRepository.register(req.getManagerUsername(), req.getManagerPassword(),
            Role.MANAGER, org.getId(), "ACTIVE");
        return Result.ok(org.getId());
    }
```
注:`repo.save` 内已校验 disorderTypes(Task 2)。两步非事务原子(单体内同请求,失败概率低);若要严格事务可加 @Transactional——本计划加 `@Transactional` 在 controller 方法或抽到一个 service 方法(实现者择一,推荐抽 OrganizationAppService.createWithManager 加 @Transactional,使机构与管理员同成败)。**推荐抽 service 加事务**,避免建了机构却没建成管理员的半成品。

- [x] **Step 3: 测试**

OrganizationApiTest 适配 + 新增:
- 超管一体建机构(带 disorderTypes/province/city + managerUsername/password)→ 200 返回 orgId;之后该 manager 能登录(role MANAGER、orgId=新机构)。
- 缺管理员账号 → 400。
- 管理员用户名已存在 → 400。
- MANAGER 调 POST /api/orgs → 403(端点级不变)。
- 公开机构列表/超管列表返回含 disorderTypes/province/city。

- [x] **Step 4: 全量回归 + 提交**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/java/com/sellm/org/ backend/src/test/java/com/sellm/org/OrganizationApiTest.java && git commit -q -m "feat(org): 超管一体创建机构+机构管理员(事务原子)"
```

---

### Task 4: 班级实体 + 班级管理(本机构 CRUD,TDD)

新增 class(班级)实体:id/name/org_id/disorder_types。机构管理员维护本机构班级(增删改查);超管可看任意机构班级(行级:管理员限本机构)。

**Files:**
- Modify: `backend/src/main/resources/schema.sql`(class 表)
- Create: `backend/src/main/java/com/sellm/clazz/Clazz.java`
- Create: `backend/src/main/java/com/sellm/clazz/ClazzMapper.java` + `mybatis/ClazzMapper.xml`
- Create: `backend/src/main/java/com/sellm/clazz/ClazzRepository.java`
- Create: `backend/src/main/java/com/sellm/clazz/ClazzController.java`
- Create: `backend/src/main/java/com/sellm/clazz/dto/{ClazzRequest,ClazzResponse}.java`
- Modify: `backend/src/main/java/com/sellm/security/SecurityConfig.java`(/api/classes 授权)
- Test: `backend/src/test/java/com/sellm/clazz/ClazzApiTest.java`

> 包名用 `clazz`(class 是 Java 关键字)。

- [x] **Step 1: schema class 表**
```sql
CREATE TABLE IF NOT EXISTS class_room (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    name           VARCHAR(128) NOT NULL,
    org_id         BIGINT NOT NULL,
    disorder_types VARCHAR(256),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```
注:表名用 `class_room`(class 是多数库保留字)。

- [x] **Step 2: Clazz 实体**

`Clazz`(id/name/orgId/disorderTypes + getter/setter + 全参构造 + 无参构造)。

- [x] **Step 3: Mapper + XML**

ClazzMapper:insert(map,回填id)、findById、findByOrg(List<Map>)、update(map)、deleteById。XML 显式 resultMap(class_room 列 → 驼峰),按计划惯例。

- [x] **Step 4: ClazzRepository**

save(回填id)/findById/listByOrg/update/deleteById;save/update 前 `DisorderType.validateCsv`。

- [x] **Step 5: ClazzController(行级:管理员限本机构)**

`@RequestMapping("/api/classes")`,注入 ClazzRepository + CurrentUser。
- POST 建班级:orgId 取 `me.getOrgId()`(管理员本机构,不信任请求);校验非空。
- GET 列表:管理员→listByOrg(me.getOrgId());超管→可带 ?orgId= 查指定机构(或返回全部,本计划:超管必须带 orgId 查某机构,简化)。
- PUT/{id}、DELETE/{id}:先 findById,校验 `clazz.orgId == me.getOrgId()`(超管放行任意),否则 ACCESS_DENIED;再改/删。
DTO:ClazzRequest(name/disorderTypes)、ClazzResponse(id/name/orgId/disorderTypes)。

- [x] **Step 6: SecurityConfig**

`/api/classes/**` 的 POST/PUT/DELETE 限 hasAnyRole("SUPER_ADMIN","MANAGER");GET 限同样(班级管理是管理员/超管功能;老师后续才需读班级,届时再放开)。放在 /api/** authenticated 之前。

- [x] **Step 7: 测试 ClazzApiTest**

- 管理员建本机构班级(orgId 自动本机构)→ 200;列表含它。
- 管理员改/删本机构班级 → 成功;改/删他机构班级 → 403(行级)。
- 建班级带非法障碍类型码 → 400。
- TEACHER 建班级 → 403(端点级)。
- 班级 disorderTypes 多选(如 "ASD,LANGUAGE")存取一致。
用 AuthTestSupport 造管理员(两机构)、老师。

- [x] **Step 8: 全量回归 + 提交**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/resources/schema.sql backend/src/main/java/com/sellm/clazz/ backend/src/main/resources/mybatis/ClazzMapper.xml backend/src/main/java/com/sellm/security/SecurityConfig.java backend/src/test/java/com/sellm/clazz/ClazzApiTest.java && git commit -q -m "feat(class): 班级实体 + 本机构班级管理(行级校验)"
```

---
### Task 5: 老师绑班级 + 管理员查看本机构家长(后端,TDD)

管理员建老师时多选班级(teacher_class 关联);管理员查看本机构家长列表。

**Files:**
- Modify: `backend/src/main/resources/schema.sql`(teacher_class 关联表)
- Create: `backend/src/main/java/com/sellm/user/TeacherClassMapper.java` + `mybatis/TeacherClassMapper.xml`
- Modify: `backend/src/main/java/com/sellm/user/dto/CreateUserRequest.java`(加 classIds:List<Long>)
- Modify: `backend/src/main/java/com/sellm/user/UserManagementController.java`(建老师后写 teacher_class;新增本机构家长列表端点)
- Modify: `backend/src/main/java/com/sellm/user/UserRepository.java`(listByOrgAndRole 或复用 listByOrg 过滤)
- Create: `backend/src/main/java/com/sellm/user/dto/ParentResponse.java`
- Modify: `backend/src/main/java/com/sellm/security/SecurityConfig.java`(家长列表端点授权)
- Test: `backend/src/test/java/com/sellm/user/TeacherClassApiTest.java`、`ParentListApiTest.java`

- [x] **Step 1: schema teacher_class**
```sql
CREATE TABLE IF NOT EXISTS teacher_class (
    teacher_user_id BIGINT NOT NULL,
    class_id        BIGINT NOT NULL,
    PRIMARY KEY (teacher_user_id, class_id)
);
```

- [x] **Step 2: TeacherClassMapper + XML**

`insert(@Param Long teacherUserId, @Param Long classId)`、`findClassIdsByTeacher(@Param Long teacherUserId)`→List<Long>、`deleteByTeacher(@Param Long teacherUserId)`(改绑用)。XML 对应。

- [x] **Step 3: CreateUserRequest 加 classIds**

加 `private List<Long> classIds;` + getter/setter(建老师时可选多选班级)。

- [x] **Step 4: UserManagementController 建老师写关联**

create 方法里,MANAGER 建 TEACHER 成功后(拿到新 userId),若 req.getClassIds() 非空:校验这些 class 都属本机构(用 ClazzRepository.findById 逐个校验 orgId==me.getOrgId(),否则 ACCESS_DENIED 防绑他机构班级),再逐个 teacherClassMapper.insert(userId, classId)。
注:建 PARENT 不绑班级(家长经儿童关联,阶段 C)。注入 ClazzRepository、TeacherClassMapper。

- [x] **Step 5: 本机构家长列表端点**

`GET /api/users/parents`(MANAGER):返回本机构 role=PARENT 的用户(ParentResponse:id/username/role/orgId/status;完整字段[姓名/儿童/关系/班级]待阶段 C)。UserRepository 加 `listByOrgAndRole(orgId, roleName)` 或复用 listByOrg 在 controller 过滤 role==PARENT。

- [x] **Step 6: SecurityConfig**

`GET /api/users/parents` hasRole("MANAGER")(放在 /api/users 通配规则前,避免被 GET /api/users 的规则覆盖——注意 /parents 比 /users 更具体)。

- [x] **Step 7: 测试**

`TeacherClassApiTest`:管理员建老师带 classIds(本机构班级)→ 成功,findClassIdsByTeacher 返回这些班级;带他机构班级 id → 403。
`ParentListApiTest`:本机构有 PARENT(造一个 ACTIVE 家长归本机构)→ 管理员 GET /api/users/parents 含它、不含他机构家长;TEACHER 调 → 403。

- [x] **Step 8: 全量回归 + 提交**

```bash
cd "D:/works/test/SELLM" && git add backend/src/main/resources/schema.sql backend/src/main/java/com/sellm/user/ backend/src/main/resources/mybatis/TeacherClassMapper.xml backend/src/main/java/com/sellm/security/SecurityConfig.java backend/src/test/java/com/sellm/user/ && git commit -q -m "feat(user): 老师绑班级(多选,本机构校验)+ 管理员查看本机构家长"
```

---

### Task 6: 前端 — 管理员菜单改造 + 班级管理页 + 用户管理扩展 + 机构建表单扩字段

**Files:**
- Create: `frontend/src/api/meta.js`(障碍类型枚举常量,前端展示用)
- Create: `frontend/src/api/classes.js`
- Modify: `frontend/src/api/orgs.js`、`users.js`(扩字段/端点)
- Modify: `frontend/src/layouts/MainLayout.vue`(菜单按角色)
- Create: `frontend/src/views/ClassesView.vue`
- Modify: `frontend/src/views/UsersView.vue`(超管建机构表单扩字段;管理员区:建老师选班级 + 家长列表)
- Modify: `frontend/src/router/index.js`(/classes 路由)

- [x] **Step 1: meta.js 障碍类型枚举**

前端常量(与后端 DisorderType 对齐):
```js
export const DISORDER_TYPES = [
  { code: 'ASD', label: '孤独症' },
  { code: 'DEVELOPMENTAL_DELAY', label: '发育迟缓' },
  { code: 'INTELLECTUAL', label: '智力障碍' },
  { code: 'LANGUAGE', label: '语言障碍' },
  { code: 'SENSORY_INTEGRATION', label: '感统失调' },
  { code: 'CEREBRAL_PALSY', label: '脑瘫' },
  { code: 'ADHD', label: '注意缺陷多动障碍' },
  { code: 'HEARING_VISION', label: '听视障' }
]
export const disorderLabel = (code) => (DISORDER_TYPES.find(d => d.code === code) || {}).label || code
export const disorderCsvToLabels = (csv) => !csv ? '' :
  csv.split(',').map(c => disorderLabel(c.trim())).join('、')
```

- [x] **Step 2: classes.js API**
```js
import http from './http'
export const listClasses = (orgId) => http.get('/classes', orgId ? { params: { orgId } } : {})
export const createClass = (payload) => http.post('/classes', payload)
export const updateClass = (id, payload) => http.put(`/classes/${id}`, payload)
export const deleteClass = (id) => http.delete(`/classes/${id}`)
```
users.js 加 `listParents = () => http.get('/users/parents')`;createUser 已支持透传 classIds。orgs.js 的 createOrg 透传新字段。

- [x] **Step 3: MainLayout 菜单按角色**

菜单项按角色精确渲染:
- SUPER_ADMIN:儿童档案、量表库管理(占位/留 B)、用户管理。
- MANAGER:儿童档案、**班级管理**、用户管理。
- TEACHER:儿童档案、评估、IEP、用户管理。
- 用 `v-if` 按 `auth.role` 控制每项。(量表库管理菜单可先指向占位页或留到 B;本计划管理员重点是班级管理。)
> 本计划重点保证 **MANAGER 看到 儿童档案/班级管理/用户管理 三项**,评估/报告/IEP 不再对 MANAGER 显示。

- [x] **Step 4: ClassesView.vue(班级管理)**

el-table 列班级(名称、障碍类型用 disorderCsvToLabels 显示)+ 新建/编辑 dialog(name + 障碍类型 el-select multiple,提交时 join 成逗号串)+ 删除确认。onMounted listClasses。管理员用(本机构)。

- [x] **Step 5: UsersView 扩展**

- 超管建机构表单:加 障碍类型(多选)、省份、地市、机构管理员账号/密码 字段。
- 管理员区:建老师表单加"所属班级"(多选,选项来自 listClasses;提交 classIds);新增"本机构家长"列表(listParents,只读,展示现有字段)。

- [x] **Step 6: router 加 /classes**

children 子路由组加 `{ path: 'classes', component: () => import('../views/ClassesView.vue') }`。

- [x] **Step 7: 构建验证 + 提交**

`cd "D:/works/test/SELLM/frontend" && npm run build` 成功。
```bash
cd "D:/works/test/SELLM" && git add frontend/src/ && git commit -q -m "feat(frontend): 管理员菜单(儿童档案/班级管理/用户管理)+ 班级管理页 + 建老师选班级 + 机构建表单扩字段 + 家长列表"
```

---

### Task 7: 端到端联调验证(组织模型 + 班级)

起 dev 后端 + curl 走:超管一体建机构(带障碍类型/省市+管理员)→ 管理员登录 → 建班级(多选障碍类型)→ 建老师绑班级 → 看本机构家长列表。记录结果,停服务。

- [x] **Step 1: 起 dev 后端(后台,./mvnw)**;**注意**:schema 加了列与新表,dev H2 文件库靠 ALTER IF NOT EXISTS / CREATE IF NOT EXISTS 兼容;若启动报列/表问题,删 backend/data/ 重启重建。
- [x] **Step 2: curl 链路**(英文机构名避编码坑):超管登录→POST /api/orgs(name/disorderTypes:"ASD,ADHD"/province/city/managerUsername/managerPassword)→管理员登录(确认 role MANAGER+orgId)→POST /api/classes(name/disorderTypes)→GET /api/classes 含它→POST /api/users 建 TEACHER 带 classIds→GET /api/users/parents。逐条核对预期。
- [x] **Step 3: 停服务**(TaskStop + 必要时 taskkill 8080)+ 追加 INTEGRATION.md。
```bash
cd "D:/works/test/SELLM" && git add frontend/INTEGRATION.md && git commit -q -m "docs: 阶段A(组织模型+班级)端到端联调结果" --allow-empty
```

---

## 后续(阶段 B–E,不在本计划)

- B 量表库管理(含菜单"量表库管理"实页)、C 家长注册改造+老师审核(填充家长姓名/儿童/关系/班级字段)、D 儿童档案大扩展、E 真实 AI+评估/IEP/家庭IEP。

## 自检结论

- **范围覆盖**:DisorderType 枚举、机构扩字段、一体建机构+管理员(事务)、班级实体CRUD(行级)、老师绑班级(本机构校验)、管理员看本机构家长、前端菜单改造+班级页+建老师选班级+机构表单扩字段。覆盖阶段 A 全部确认项。
- **红线**:行级权限(班级/老师/家长查询限本机构,超管跨机构);障碍类型码后端校验;一体建机构事务原子;建老师绑班级校验班级属本机构(防越权)。
- **兼容**:Organization 加字段保留旧构造器;CreateUserRequest 加 classIds 可选;Role 不变;既有 122 测试经适配保绿。
- **占位符**:无 TBD;Task 3 的事务实现给了"抽 service 加 @Transactional"明确方向;家长完整字段明确标注待阶段 C。
- **家长字段边界**:本计划"看家长"展示现有字段,完整字段(姓名/儿童/关系/班级)依赖阶段 C 的家长注册改造,已说明,避免本计划重复改家长表。
