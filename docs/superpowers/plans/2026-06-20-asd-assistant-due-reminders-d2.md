# 阶段 D 批二 — 到期提醒(IEP 到期 / 复评临近)Implementation Plan

## Context

阶段 D 批一已落库 `child.reassess_date`(复评时间)和 `child.iep_due_date`(IEP 到期日)两个字段,但目前只是静态展示,没有"哪些孩子快到期了"的主动计算。批二补上这块:后端算出**距今 30 天内(含已逾期)**需复评 / IEP 到期的儿童,前端在儿童列表页顶部以提醒区集中呈现,帮助老师/管理员/家长及时跟进。这是阶段 D 的收尾,之后即可进入阶段 E(真实大模型)。

## 设计决策(已与用户确认)

1. **时间窗**:后端固定 30 天阈值。`reassess_date` 或 `iep_due_date` 落在 `[今天-∞, 今天+30天]`(即已逾期或 30 天内到期)则纳入提醒。无日期的儿童不提醒。
2. **范围**:复用 `AccessGuard` 行级权限——老师/管理员看本机构、家长看自己孩子(与 `GET /api/children` 一致)。
3. **展示**:儿童列表页(`ChildrenView.vue`)顶部加"到期提醒"区,表格列出临期儿童 + 提醒类型(复评/IEP)+ 剩余天数(逾期显红色标签)。

## 现状关键点(已探查)

- `Child` 实体(`backend/src/main/java/com/sellm/child/Child.java`):`reassessDate`/`iepDueDate` 为 ISO `yyyy-MM-dd` String;无日期时为 null。
- `ChildController.list()`(`backend/src/main/java/com/sellm/child/ChildController.java`):已用 `accessGuard.canAccess(me, c)` 做行级过滤遍历 `repository.findAll()`。提醒端点完全复用这套过滤逻辑。
- `ChildResponse`:已含 `reassessDate`/`iepDueDate`(批一加的)。
- 前端 `ChildrenView.vue`:`listChildren()` → el-table;`children.js` 已有 API 封装。
- 日期比较用 `java.time.LocalDate`(`LocalDate.parse(isoStr)` + `until(...).getDays()` 或 `ChronoUnit.DAYS.between`)。**注意**:工作流脚本禁用 `now()` 类调用,但这是普通 Spring 代码,可正常用 `LocalDate.now()`。

---

## 文件结构(本计划范围)

```
backend/src/main/java/com/sellm/child/
  ChildReminderController.java        # 新增:GET /api/children/reminders
  dto/ChildReminderResponse.java      # 新增:childId/name/reminderType/dueDate/daysLeft/overdue
backend/src/test/java/com/sellm/child/
  ChildReminderApiTest.java           # 新增
frontend/src/
  api/children.js                     # 加 listReminders()
  views/ChildrenView.vue              # 顶部提醒区
```

> 说明:不新增 schema、不改 Child 实体 / Repository(日期字段批一已就位)。提醒为纯计算端点。安全配置:`GET /api/children/reminders` 命中现有 `/api/**` authenticated 规则即可,行级在 controller;无需改 SecurityConfig(reminders 是 GET,不被 children 的写规则影响)。需确认 `/api/children/reminders` 不会被 `/api/children/{id}` 的路径变量误匹配——Spring 中静态路径段优先于路径变量,且二者都走 GET+authenticated,无授权冲突。

---

### Task 1: 后端提醒端点(TDD)

距今 30 天内(含逾期)需复评 / IEP 到期的儿童列表,行级过滤。

**Files:** ChildReminderController.java, dto/ChildReminderResponse.java, ChildReminderApiTest.java

- [ ] **Step 1:** `ChildReminderResponse`:`childId`、`name`、`reminderType`(REASSESS / IEP_DUE)、`dueDate`(ISO)、`daysLeft`(int,负=已逾期)、`overdue`(boolean)。
- [ ] **Step 2:** `ChildReminderController` `@GetMapping("/api/children/reminders")`:
  - `me = currentUser.require()`
  - 遍历 `repository.findAll()`,`accessGuard.canAccess(me, c)` 过滤(同 list())
  - 对每个有权儿童:若 `reassessDate` 非空且 `daysLeft ≤ 30`(`LocalDate.now().until(parse).getDays()`,逾期为负也纳入)→ 加一条 REASSESS;`iepDueDate` 同理加 IEP_DUE 条目
  - 解析失败(非法日期串)跳过该日期,不抛错
  - 按 daysLeft 升序(最紧急/逾期最久在前)
  - 常量 `REMIND_WITHIN_DAYS = 30`
- [ ] **Step 3:** `ChildReminderApiTest`(用 ChildRepository 直接造数据,LocalDate.now() 动态算日期串):
  - 老师本机构儿童:20 天后复评 → 含 REASSESS,daysLeft≈20,overdue=false
  - IEP 已逾期(-5 天)→ 含 IEP_DUE,overdue=true,daysLeft<0
  - 40 天后到期 → 不在列表(超窗)
  - 无日期儿童 → 不在列表
  - 他机构儿童 → 老师看不到(行级)
  - 家长只看到自己孩子的提醒
- [ ] **Step 4:** `./mvnw test` 全绿(现 186 + 新增约 4-5)→ Commit

---

### Task 2: 前端提醒区

**Files:** api/children.js, views/ChildrenView.vue

- [ ] **Step 1:** `children.js` 加 `listReminders = () => http.get('/children/reminders')`
- [ ] **Step 2:** `ChildrenView.vue` 顶部加 el-card "到期提醒":el-table 列出 姓名 / 提醒类型(复评 or IEP到期,中文)/ 到期日 / 剩余(`daysLeft>=0` 显"N天后",`<0` 显"已逾期N天"红色 el-tag);点行可跳 `/children/{id}` 详情。onMounted 调 listReminders。空时不显示该卡或显"暂无临期提醒"。
- [ ] **Step 3:** reminderType→中文映射(REASSESS=复评提醒 / IEP_DUE=IEP到期);overdue 用 `type="danger"`,否则 `warning`。
- [ ] **Step 4:** `npm run build` 通过 → Commit

---

### Task 3: 端到端联调

起 dev 后端 + curl:造不同日期的儿童(临期/逾期/超窗/无日期)→ GET /api/children/reminders 核对纳入与排序 → 他机构老师看不到。记录 INTEGRATION.md。

- [ ] **Step 1:** 起 dev 后端
- [ ] **Step 2:** curl:老师建档设 reassessDate(用相对今天的日期)→ 查 reminders 含它且 daysLeft 正确;逾期一条 overdue=true;超 30 天一条不在;他机构老师查 → 不含。
- [ ] **Step 3:** 停服务,追加 INTEGRATION.md,提交

---

## 验证清单

1. **后端全量回归** `./mvnw test` 全绿(现 186 + 新增)
2. **前端 build** `npm run build` 无错误
3. **端到端 curl** 全通(临期/逾期/超窗/无日期/行级)
4. **红线**:行级权限复用 AccessGuard(老师/管理员限本机构、家长限自己孩子);只读端点,无数据变更
5. **兼容**:不改 schema/实体/现有 child 端点;ChildrenView 既有列表/建档功能不变

## 后续(不在本计划)

- 阶段 E:真实大模型接入(评估报告 / IEP / 家庭 IEP 生成,消费儿童档案上下文 + 本阶段到期信息)
