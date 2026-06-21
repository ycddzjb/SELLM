# 教学训练 Agent(agent-teaching)设计文档

> 文档日期:2026-06-20
> 状态:设计已确认,待用户复核
> 隶属:特殊教育垂直大模型平台 · 第 1 个 Agent(教学训练)· 第二个真实业务 Agent
> 平台 Spec:`docs/spec/special_edu_llm_spec.md`(§1.2 边界 / §6.5 lesson_plan+courseware 表)
> 复用模板:`docs/superpowers/specs/2026-06-20-agent-qa-design.md`(Java 编排 + Python 智能 + 脱敏自动装配 + X-User-Id 鉴权 + 四件套)

## 1. 定位与范围

Spec 五大 Agent 的第 1 个,平台**第二个真实业务 Agent**(qa 之后)。核心职责:把 IEP 个别化方案落地为**分层教案**,并生成**适配课件**。

### 1.1 第一版范围

- **在范围**:Java sellm-agent-teaching(教案/课件的生成草案→编辑→定稿编排 + 持久化 + 鉴权 + 脱敏)+ Python 智能层 teaching 编排(mock LLM 生成教案/课件文本)+ 课件产物经对象存储落盘。
- **mock 不外联**:LLM 默认 mock;对象存储默认 Noop(本地落盘),可切 MinIO。真实 LLM/MinIO 为可切换适配器默认不启用。
- **不在第一版(YAGNI)**:富媒体课件(PPT/图片/视频——属智能教具 Agent);跨服务实时调 agent-assessment 取 IEP(改为前端传入);多轮迭代式教案优化;真实知识库 RAG 增强(教案生成首版不挂 RAG,纯 IEP+障碍类型→LLM)。

### 1.2 与 qa 模板的关系

agent-teaching **复用** qa 沉淀的全部基础设施模板:`HttpSmartLayerClient`(HTTP/1.1 + protected send)、X-User-Id 头鉴权(无 spring-security)、行级权限(资源归属创建者)、四件套 MyBatis 持久化、`QaExceptionHandler` 同构异常处理、Anonymizer 自动装配脱敏。新增的是教学业务逻辑 + 一处存储能力的自动装配扩展(见 §3.3)。

## 2. 架构与调用链

```
用户(老师)→ 网关(JWT 验签 + 注入 X-User-* + 限流)
  → Java sellm-agent-teaching:
    教案:
      POST /api/teaching/lesson-plans              生成草案(DRAFT)
      PUT  /api/teaching/lesson-plans/{id}          老师编辑
      POST /api/teaching/lesson-plans/{id}/finalize 定稿(FINALIZED)
      GET  /api/teaching/lesson-plans/{id}          查看
      GET  /api/teaching/lesson-plans               我的教案列表
    课件:
      POST /api/teaching/courseware                 基于定稿教案生成课件草案(DRAFT)
      PUT  /api/teaching/courseware/{id}            编辑
      POST /api/teaching/courseware/{id}/finalize   定稿 + 产物落对象存储
      GET  /api/teaching/courseware/{id}            查看

  生成流程(教案 / 课件草案):
    1. 读 X-User-Id;行级权限(资源归属本人)
    2. 落库 DRAFT 记录(owner_id)
    3. Anonymizer 脱敏(IEP 内容/教案文本可能含儿童姓名)→ 出网前必经
    4. REST 调 Python /v1/agents/teaching/invoke(task=lesson_plan|courseware)
    5. Python mock LLM 生成文本 → 返回
    6. Java 还原 PII → 存入 ai_draft + content → 返回 DRAFT
    定稿流程:老师编辑 content → finalize → status=FINALIZED
    课件定稿额外:把 FINALIZED content 作为产物经 ObjectStorage.put 落盘 → 存 storage_key
```

### 2.1 职责边界

- **Java agent-teaching**:鉴权、教案/课件持久化、草案/定稿状态机、脱敏编排、课件产物存储。**持 teaching 业务库**。
- **Python 智能层**:接收脱敏后的 IEP 内容 + 障碍类型 + scene/mode,mock LLM 生成教案/课件文本。**不持业务库、不鉴权、不还原占位符**。
- **跨 Agent**:teaching **不调** agent-assessment。IEP 内容 + 障碍类型由前端从评估 Agent 页面拿到后,作为生成请求体传入;`source_iep_id` 仅存引用(不解析、不回查)。

## 3. 组件设计

### 3.1 Java 侧(sellm-agent-teaching,根包 com.sellm.teaching)

| 组件 | 职责 |
|---|---|
| `LessonPlanController` / `CoursewareController` | REST 端点;X-User-Id 鉴权(@RequestHeader)|
| `TeachingAppService` | 行级权限 + 事务编排:落库、脱敏、调 Python、状态机(生成/编辑/finalize)、课件产物存储 |
| `LessonPlan` / `Courseware`(实体) | 领域对象 |
| `Scene`(枚举 HOME/SCHOOL/ORG)/ `Mode`(枚举 ONE_ON_ONE/GROUP)/ `PlanStatus`(DRAFT/FINALIZED) | 值对象 |
| `SmartLayerClient` + `HttpSmartLayerClient` | 调 Python(复用 qa 同构;teaching 自己一份)|
| `SmartLayerProperties` | sellm.teaching.smart-layer 配置 |
| `*Mapper` + XML + `*Repository`(教案/课件各一套四件套) | 持久化(镜像 report/iep)|
| `TeachingExceptionHandler` + `UnauthorizedException` | 本模块异常→Result(同构 qa)|

> 状态机沿用 IEP 模块模式:generate→DRAFT(ai_draft 存原稿、content 存可编辑稿)、PUT 改 content、finalize→FINALIZED(content 即定稿)。课件 finalize 额外触发 `ObjectStorage.put`。

### 3.2 Python 侧(ai-smart-layer/app)

| 组件 | 职责 |
|---|---|
| `app/agents/teaching.py` | teaching 编排:按 task(lesson_plan/courseware)+ IEP内容/障碍类型/scene/mode → mock LLM 生成文本 |
| `app/main.py` | 新增 `POST /v1/agents/teaching/invoke` |
| 复用 `app/adapters/llm.py` | get_llm()→MockLLM |

teaching 编排首版不挂 RAG(纯 prompt 拼装 + mock LLM),与 qa 的 RAG 管线区分;后续可加教学范例库 RAG。

### 3.3 必要扩展:ObjectStorage 自动装配(与 qa 的 Anonymizer 同类)

`ObjectStorage` **接口在 sellm-common-core**,但实现(NoopObjectStorage/MinioObjectStorage)+ StorageConfig 在 **sellm-common-backend**。agent-teaching 只依赖 core → 无 ObjectStorage bean。**沿用 qa 的自动装配模板解决**:

- 在 sellm-common-core 新增 `storage/StorageAutoConfiguration`(`@AutoConfiguration` + `@ConditionalOnMissingBean(ObjectStorage.class)` 提供默认 `NoopObjectStorage`),注册进 core 的 `AutoConfiguration.imports`。NoopObjectStorage 构造接收 `localDir` String,auto-config 用 `@Value("${sellm.storage.local-dir:data/media}")` 注入,**不引入 backend 的 StorageProperties**(core 自洽)。
- NoopObjectStorage 当前在 backend(纯 JDK NIO,无 @Component、不依赖 StorageProperties——已核实);需把它**移到 core**(与 ObjectStorage 接口同模块)。MinioObjectStorage + StorageConfig + StorageProperties 留 backend(依赖 minio SDK / @ConfigurationProperties)。
- 效果:任何依赖 core 的模块(teaching/未来教具 Agent)免依赖 backend 即得 NoopObjectStorage;backend 经 StorageConfig(@ConditionalOnMissingBean 或 provider 判断)仍可切 MinIO。
- 这把 P2 "实现在 backend"对存储能力的缺口补上,是模板的合理延伸(qa 已对 Anonymizer 做过)。

> backend 影响校验:backend 现经组件扫描/StorageConfig 装配 ObjectStorage;移走 NoopObjectStorage 后,backend 仍能经其 StorageConfig(在 backend)装配 Noop 或 Minio。需确保 backend 仍恰好一个 ObjectStorage bean、媒体相关测试不破。实现期重点回归。

## 4. 数据模型(Spec §6.5,镜像 report/iep 四件套)

`lesson_plan`:
- `id` BIGINT PK AUTO_INCREMENT
- `owner_id` BIGINT(创建老师;行级权限依据)
- `child_id` BIGINT NULL(一对一时)/ `class_id` BIGINT NULL(集体课时)
- `source_iep_id` BIGINT NULL(IEP 引用,不回查)
- `scene` VARCHAR(HOME/SCHOOL/ORG)
- `mode` VARCHAR(ONE_ON_ONE/GROUP)
- `disorder_type` VARCHAR(障碍类型,生成时传入)
- `ai_draft` TEXT(AI 原稿,留痕)
- `content` TEXT(可编辑稿 / 定稿)
- `status` VARCHAR(DRAFT/FINALIZED)
- `created_at` / `updated_at` / `deleted`

`courseware`:
- `id` BIGINT PK AUTO_INCREMENT
- `owner_id` BIGINT
- `lesson_plan_id` BIGINT(来源定稿教案)
- `disorder_type` VARCHAR
- `ai_draft` TEXT / `content` TEXT
- `storage_key` VARCHAR NULL(finalize 后产物对象 key)
- `format` VARCHAR(首版 TEXT/HTML)
- `status` VARCHAR(DRAFT/FINALIZED)
- `created_at` / `updated_at` / `deleted`

> MyBatis 风格沿用:Mapper 收发 `Map<String,Object>`;XML resultMap snake→camel;insert useGeneratedKeys 写回 id;Repository 手动类型转换。schema.sql 放 sellm-agent-teaching/resources。

## 5. 接口规范

### 5.1 端点(经网关 `/api/teaching/**` → agent-teaching:8081)

**POST /api/teaching/lesson-plans** — 生成教案草案
```json
请求: {
  "childId": 12, "classId": null, "sourceIepId": 34,
  "scene": "SCHOOL", "mode": "ONE_ON_ONE", "disorderType": "ASD",
  "iepContent": "长期目标:提升共同注意……短期目标:……"   // 前端从评估 Agent 拿到
}
响应 data: { "id": 1, "status": "DRAFT", "content": "(AI 生成的分层教案文本)", "aiDraft": "..." }
```

**PUT /api/teaching/lesson-plans/{id}** — 编辑(改 content)
```json
请求: { "content": "(老师修改后的教案)" } → data: { id, status: "DRAFT", content }
```

**POST /api/teaching/lesson-plans/{id}/finalize** — 定稿
```json
→ data: { id, status: "FINALIZED", content }
```

**GET /api/teaching/lesson-plans/{id}** / **GET /api/teaching/lesson-plans** — 查看/我的列表

**课件**(基于 FINALIZED 教案):
- `POST /api/teaching/courseware`(body: { lessonPlanId, format? })→ 生成课件草案(读教案 content 作输入)
- `PUT /api/teaching/courseware/{id}`(编辑)
- `POST /api/teaching/courseware/{id}/finalize`(定稿 + 产物落 ObjectStorage,回填 storage_key)
- `GET /api/teaching/courseware/{id}`

### 5.2 Java→Python 内部接口

**POST /v1/agents/teaching/invoke**(Python,内网)
```json
请求: {
  "task": "lesson_plan",                    // 或 "courseware"
  "iepContent": "(脱敏后)", "disorderType": "ASD",
  "scene": "SCHOOL", "mode": "ONE_ON_ONE",
  "lessonPlanContent": null                 // task=courseware 时传脱敏后教案文本
}
响应: { "content": "(生成文本)", "mock": true }
```

### 5.3 通用约定(继承平台)

- 统一 `Result<T>` 信封;错误码用 core 现有 + qa 已加的 NOT_FOUND/UNAUTHORIZED;脱敏失败 ANONYMIZATION_FAILED。
- 网关注入 X-User-Id;agent-teaching 信任并解析(无 spring-security)。
- 课件 storage_key 经 `ObjectStorage.reference(key)` 给前端可访问引用(Noop 为本地路径,MinIO 为预签名 URL)。

## 6. 红线落地(继承平台三红线)

1. **AI 只产草案,人工定稿**:教案/课件 generate 产 DRAFT(ai_draft 留 AI 原稿、content 可编辑);老师 PUT 编辑;finalize→FINALIZED。**课件产物落存储仅在 finalize**;非 FINALIZED 不导出。
2. **出网必经脱敏**:generate 调 Python 前,Java 对 iepContent(教案任务)/ lessonPlanContent(课件任务)经 Anonymizer 脱敏(姓名/学校/身份证/手机/邮箱→占位符);脱敏失败硬阻断(ANONYMIZATION_FAILED,不调 Python);返回文本还原。
3. **Python 不持明文**:Python 不持久化、不回写、不还原占位符;teaching 业务数据落库在 Java 侧(还原后)。

## 7. 错误处理

| 环节 | 策略 |
|---|---|
| 脱敏失败 | 硬阻断(ANONYMIZATION_FAILED→400),不调 Python。顺序:先落 DRAFT 记录(content 空)→ 脱敏 → 调 Python。脱敏失败时 DRAFT 已存(content 空),返回错误;老师可重试生成或手动填 content。与 qa 一致(业务记录先落、AI 步骤可失败)|
| Python 不可用/超时 | 优雅降级:content 置"AI 生成失败,可重试或手动撰写";DRAFT 记录保留,老师可手动填 content 再 finalize |
| 课件基于非 FINALIZED 教案 | 拒绝(400):课件须基于定稿教案 |
| 导出非 FINALIZED 课件 | 拒绝(400)|
| 他人资源 / 不存在 | 403(ACCESS_DENIED)/ 404(NOT_FOUND)|
| ObjectStorage 失败 | finalize 课件时存储失败 → 返回错误,status 不置 FINALIZED(保持可重试)|

## 8. 测试策略

### 8.1 Java(@SpringBootTest + MockMvc,@ActiveProfiles("test"),H2)
- 生成教案→DRAFT(注入 SmartLayerClient 桩返回文本);PUT 编辑 content;finalize→FINALIZED。
- 脱敏:断言传给 Python 的 iepContent 是脱敏后(含占位符,身份证/手机被替换);返回还原。
- 课件:基于 FINALIZED 教案生成草案;finalize 触发 ObjectStorage.put(Noop 桩)回填 storage_key;基于非 FINALIZED 教案生成→400。
- 行级权限:他人教案/课件 GET/编辑/finalize→403;不存在→404。
- 降级:SmartLayerClient 桩抛异常→DRAFT 保留 + content 降级提示。
- 脱敏硬阻断:Anonymizer 桩抛 AnonymizationException→400 且不调 Python(独立测试类,@Primary 抛异常桩,同 qa)。
- token/鉴权:缺 X-User-Id→401。

### 8.2 Python(pytest,mock)
- teaching 编排:task=lesson_plan / courseware 各生成非空文本;mock LLM;占位符不还原(Python 不持明文)。
- /v1/agents/teaching/invoke 端点返回 content。

### 8.3 质量门禁
- 全 reactor `clean install`(clean,避免 stale)SUCCESS;agent-teaching 模块测试绿;**backend 仍绿(NoopObjectStorage 移 core 后重点回归)**;Python pytest 绿或静态审查(无 3.11 环境)。

## 9. 模块归属与依赖

- teaching 业务全在 **sellm-agent-teaching** 模块(com.sellm.teaching 包),依赖 sellm-common-core。
- core 新增 StorageAutoConfiguration + NoopObjectStorage 迁入(§3.3)。
- agent-teaching pom 需加:mybatis/jdbc/h2(持久化)。jackson 来自 spring-boot-starter-web(已有)。Anonymizer/ObjectStorage 经 core 自动装配获得,无需依赖 backend。

## 10. 实现前明确(不阻塞设计)

- agent-teaching 数据源:dev/test H2;prod MySQL。
- Python teaching 编排首版无 LangGraph 状态图(单步 prompt 拼装即可;YAGNI)。
- 课件 format 首版仅 TEXT/HTML;富媒体后续。
- StorageProperties 在 backend;core 的 NoopObjectStorage 默认本地目录(可走 ${SELLM_MINIO_LOCAL_DIR} 或 teaching 自己的配置;实现期定最简形式,默认相对 data/ 目录)。
