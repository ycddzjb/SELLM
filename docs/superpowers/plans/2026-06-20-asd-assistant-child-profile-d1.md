# 阶段 D(批一)— 儿童档案扩展字段 + 时间序列记录 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 儿童档案从「姓名/障碍类型/机构/监护人」扩展为含基线评估、年度 IEP 概要、月度干预目标、复评时间、干预进度等结构化字段;新增统一的时间序列记录表 `child_log`(课堂追踪 / 家校沟通 / 阶段复盘,用 type 区分)的 CRUD;前端档案详情页展示与维护这些字段和记录。

**范围说明:** 本计划是阶段 D 的**批一**。评估历史 / 报告历史 / IEP 历史已在现有 ChildDetailView 聚合(无需重做)。**到期提醒(IEP 到期/复评提醒的主动计算与提示)留批二**,本批只落库"复评时间/IEP 到期日"字段,前端静态展示。

**Architecture:** 延续模块化单体。child 表加结构化静态字段(非 PII 的概要文本);新增 `child_log` 表(单表 + type 枚举)。记录读写复用 `AccessGuard.checkChildAccess` 行级权限(老师/管理员限本机构、家长限自己孩子)。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / MyBatis / H2(test) / Vue 3 + Element Plus。根包 `com.sellm`。

**Source spec:** `docs/superpowers/specs/2026-06-19-asd-assistant-v2-requirements.md` 第 6 节

---

## 设计决策(已与用户确认)

1. **分批**:本计划只做批一(child 扩字段 + child_log 记录表 CRUD);到期提醒主动计算留批二。
2. **记录单表**:`child_log(id, child_id, log_type, content, author_user_id, created_at)`,`log_type` ∈ {CLASSROOM_TRACK 课堂追踪, HOME_COMMUNICATION 家校沟通, STAGE_REVIEW 阶段复盘}。统一结构,新增记录类型只加枚举值。
3. **记录权限**:CRUD 走 `AccessGuard.checkChildAccess`(老师/管理员限本机构、家长限自己孩子);沿用现有行级模型,不新增"仅老师写"限制。
4. **child 扩字段**(结构化概要文本,非 PII,不加密):`baseline_summary`(基线评估概要)、`annual_iep_summary`(年度 IEP 方案概要)、`monthly_goal`(月度干预目标)、`reassess_date`(复评时间,DATE)、`iep_due_date`(IEP 到期日,DATE)、`intervention_progress`(干预进度,VARCHAR)。儿童姓名仍加密(现状不变)。
5. **LogType 枚举**:带中文标签 + 校验工具(参照 DisorderType/Relationship 范式)。

---

## 与现状的关键变更

- `child` 表加 6 列(ALTER IF NOT EXISTS);`Child` 实体、ChildMapper.xml、ChildRepository 同步;ChildRequest/ChildResponse 扩字段
- 新增 `com.sellm.child.log` 子模块:LogType 枚举(放 common)、ChildLog 实体、Mapper/XML、Repository、Controller、DTO
- `ChildController` 的 update 支持新字段(归属字段仍不可改)
- 新增 `/api/children/{childId}/logs` 端点(GET 列表 / POST 新增 / DELETE)
- 前端 ChildDetailView 增"档案字段编辑"区 + "记录"区(三类 tab/分段);ChildrenView 列表可不变

---

## 文件结构(本计划范围)

```
backend/src/main/
  resources/
    schema.sql                         # child 加 6 列;新增 child_log 表
    mybatis/ChildMapper.xml            # 读写新列
    mybatis/ChildLogMapper.xml         # 新增
  java/com/sellm/
    common/LogType.java                # 记录类型枚举
    child/
      Child.java                       # 加 6 字段
      ChildRepository.java             # 读写新字段
      ChildController.java             # update 支持新字段
      dto/ChildRequest.java            # 扩字段
      dto/ChildResponse.java           # 扩字段
      log/
        ChildLog.java
        ChildLogMapper.java
        ChildLogRepository.java
        ChildLogController.java        # /api/children/{childId}/logs
        dto/ChildLogRequest.java
        dto/ChildLogResponse.java
  security/SecurityConfig.java         # /api/children/*/logs 授权(写 TEACHER/MANAGER,读 authenticated+行级)
backend/src/test/java/com/sellm/
    common/LogTypeTest.java
    child/ChildRepositoryTest.java     # 扩字段断言
    child/ChildExtendedFieldsApiTest.java
    child/log/ChildLogApiTest.java     # CRUD + 行级 + 类型过滤
frontend/src/
    api/children.js                    # logs 端点
    api/meta.js                        # LOG_TYPES 枚举
    views/ChildDetailView.vue          # 字段编辑 + 记录区
```

---

### Task 1: LogType 枚举 + schema(child 扩列 + child_log 表)(TDD)

**Files:** common/LogType.java, schema.sql, LogTypeTest.java

- [x] **Step 1:** `LogType` 枚举:CLASSROOM_TRACK("课堂追踪")、HOME_COMMUNICATION("家校沟通")、STAGE_REVIEW("阶段复盘");getLabel + validate + labelOf(参照 Relationship)
- [x] **Step 2:** schema.sql:
```sql
ALTER TABLE child ADD COLUMN IF NOT EXISTS baseline_summary VARCHAR(1024);
ALTER TABLE child ADD COLUMN IF NOT EXISTS annual_iep_summary VARCHAR(1024);
ALTER TABLE child ADD COLUMN IF NOT EXISTS monthly_goal VARCHAR(1024);
ALTER TABLE child ADD COLUMN IF NOT EXISTS reassess_date DATE;
ALTER TABLE child ADD COLUMN IF NOT EXISTS iep_due_date DATE;
ALTER TABLE child ADD COLUMN IF NOT EXISTS intervention_progress VARCHAR(128);

CREATE TABLE IF NOT EXISTS child_log (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    child_id       BIGINT NOT NULL,
    log_type       VARCHAR(32) NOT NULL,
    content        VARCHAR(2048),
    author_user_id BIGINT,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```
- [x] **Step 3:** LogTypeTest(3 类齐全 + 标签 + 非法码抛异常 + labelOf 兜底)
- [x] **Step 4:** 全量回归绿 → Commit

---

### Task 2: Child 扩字段(实体 + Mapper + Repository + DTO)(TDD)

**Files:** Child.java, ChildMapper.xml, ChildRepository.java, dto/ChildRequest.java, dto/ChildResponse.java, ChildController.java, ChildRepositoryTest.java

- [x] **Step 1:** Child 实体加 6 字段 + getter/setter;**保留现有构造器**(5 参 id/name/disorder/org/guardian 委托,新字段 null),加全参或用 setter 装配
- [x] **Step 2:** ChildMapper.xml:resultMap 加 6 列;insert/update/select 带新列(reassess_date/iep_due_date 用 DATE)
- [x] **Step 3:** ChildRepository save/update/toChild 带新字段(姓名仍加密,新字段明文)
- [x] **Step 4:** ChildRequest/ChildResponse 加 6 字段;ChildController.update 写入新字段(归属 orgId/guardian 仍不可改)
- [x] **Step 5:** ChildRepositoryTest 扩:save 带新字段 → findById 读回一致(日期、概要)
- [x] **Step 6:** ChildExtendedFieldsApiTest:老师建档/改档带新字段 → GET 返回;行级不变
- [x] **Step 7:** 全量回归绿 → Commit

---

### Task 3: child_log 记录 CRUD(后端,TDD)

**Files:** child/log/*.java, mybatis/ChildLogMapper.xml, SecurityConfig.java, ChildLogApiTest.java

- [x] **Step 1:** ChildLog 实体(id/childId/logType/content/authorUserId/createdAt)+ Mapper(insert/findByChild/findByChildAndType/findById/deleteById)+ XML
- [x] **Step 2:** ChildLogRepository(save 回填 id;listByChild;listByChildAndType;findById;deleteById)
- [x] **Step 3:** ChildLogController `@RequestMapping("/api/children/{childId}/logs")`:
  - GET(可选 ?type= 过滤):先 findById child + AccessGuard.checkChildAccess(行级);列出记录
  - POST:校验 child 存在 + 行级;LogType.validate;author=当前用户;保存
  - DELETE /{logId}:校验 log 属该 child + 行级;删除
  - DTO:ChildLogRequest(logType/content)、ChildLogResponse(id/logType/logTypeLabel/content/authorUserId/createdAt)
- [x] **Step 4:** SecurityConfig:`/api/children/*/logs/**` 写(POST/DELETE)限 TEACHER/MANAGER;GET authenticated(行级在 controller)。放在 /api/children 既有规则之前
- [x] **Step 5:** ChildLogApiTest:
  - 老师对本机构儿童加记录(三类)→ 成功;按 type 过滤正确
  - 老师对他机构儿童加记录 → 403(行级)
  - 家长对自己孩子加记录 → 成功;对别人孩子 → 403
  - 非法 logType → 400
  - 删除本机构儿童记录 → 成功
- [x] **Step 6:** 全量回归绿 → Commit

---

### Task 4: 前端 — 档案字段编辑 + 记录区

**Files:** api/children.js, api/meta.js, views/ChildDetailView.vue

- [x] **Step 1:** meta.js 加 `LOG_TYPES`(code+label);children.js 加 listChildLogs(childId, type?)/createChildLog/deleteChildLog
- [x] **Step 2:** ChildDetailView 顶部 el-descriptions 增展示新字段(基线/年度IEP/月度目标/复评时间/IEP到期/干预进度);加"编辑档案字段"dialog(复用 updateChild,提交 6 字段)
- [x] **Step 3:** 新增"成长记录"区:el-tabs(课堂追踪/家校沟通/阶段复盘)或带 type 筛选的表;每类可新增(content 文本 + 提交)/删除;按 created_at 倒序展示
- [x] **Step 4:** `npm run build` 通过 → Commit

---

### Task 5: 端到端联调

起 dev 后端 + curl:老师建档(带扩展字段)→ 改档 → 加三类记录 → 按 type 查 → 家长对自己孩子加记录 → 他机构老师加记录 403 → 删除。记录 INTEGRATION.md。

- [x] **Step 1:** 起 dev 后端(schema 加列/表,ALTER/CREATE IF NOT EXISTS 兼容)
- [x] **Step 2:** curl 链路(扩字段建档/改档读回、三类记录 CRUD、type 过滤、行级 403)
- [x] **Step 3:** 停服务,追加 INTEGRATION.md,提交

---

## 验证清单

1. **后端全量回归** `./mvnw test` 全绿(现 173 + 新增约 12-15)
2. **前端 build** `npm run build` 无错误
3. **端到端 curl** 全通
4. **红线**:儿童姓名仍加密(新字段为非 PII 概要,明文);记录 CRUD 行级(AccessGuard);归属字段不可越权改
5. **兼容**:现有 child 5 参构造/评估流程不破;ChildDetailView 既有评估/报告/IEP 历史区不变

---

## 后续(不在本计划)

- 阶段 D 批二:到期提醒主动计算(IEP 到期/复评临近列表与提示)
- 阶段 E:真实大模型接入(评估报告 / IEP / 家庭 IEP 生成,消费本阶段的儿童档案上下文)
