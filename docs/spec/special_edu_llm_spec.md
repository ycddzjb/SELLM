# 特殊教育垂直领域大模型平台 — 标准化 Spec

> 文档日期:2026-06-20
> 状态:设计已确认,待用户复核
> 适用范围:平台级纲领文档(跨五大 Agent + 数据知识底座)
> 隶属:南京特殊教育师范学院 · 特殊教育大模型研发项目
> 前序文档:`docs/superpowers/specs/2026-06-18-asd-assessment-iep-assistant-design.md`(评估干预第一版)、`docs/superpowers/specs/2026-06-19-asd-assistant-v2-requirements.md`(v2 需求全景)

---

## 0. 文档导航

| 章节 | 内容 | 对应需求项 |
|---|---|---|
| 1 | 平台定位与业务边界 | 业务边界 |
| 2 | 用户角色与权限模型 | 用户角色 |
| 3 | 技术栈选型 | 技术栈选型 |
| 4 | 多 Agent 通信架构 | 多Agent通信架构 |
| 5 | 前后端接口规范 | 前后端接口规范 |
| 6 | 数据库表结构 | 数据库表结构 |
| 7 | 向量库设计 | 向量库设计 |
| 8 | 小程序适配约束 | 小程序适配约束 |
| 9 | 安全规范 | 安全规范 |
| 10 | 测试标准 | 测试标准 |
| 11 | 分期实现路线图 | — |
| 12 | 术语表 | — |

---

## 1. 平台定位与业务边界

### 1.1 平台定位

面向全国特殊教育学校与康复机构,建设覆盖 **8 类障碍**(孤独症谱系 / 发育迟缓 / 智力障碍 / 语言障碍 / 感觉统合失调 / 脑瘫 / ADHD / 听视障)的特殊教育垂直领域大模型平台。平台以**多 Agent 分布式架构**承载五大业务场景,辅以**数据与知识底座**(特教知识库 + RAG + 合规框架),通过 **Web 全功能管理后台**与**微信小程序轻量终端**双端触达管理员、特教教师、康复师、家长四类用户。

### 1.2 五大 Agent 业务边界

平台由五个**职责单一、边界清晰、可独立部署与演进**的 Agent 组成。每个 Agent = 业务服务(Java,负责数据/权限/流程/落库)+ 可选 AI 智能层(Python,负责 LLM 编排/RAG/多模态)。

| # | Agent | 核心职责 | 边界(做什么) | 不做什么(YAGNI / 归属他 Agent) |
|---|---|---|---|---|
| 1 | **教学训练 Agent** `agent-teaching` | 教学与训练场景的备课与教学设计 | IEP 个别化方案落地为分层教案;一对一/集体课教学设计;家庭/学校/机构三类场所适配;障碍儿童适配课件生成;课堂训练任务编排 | 不做评估计分(→ 评估干预);不做教具素材生成(→ 智能教具) |
| 2 | **评估干预 Agent** `agent-assessment` | 标准化评估与 IEP 干预 | 8 类障碍标准化量表评估;多模态识别(视频/照片/笔记)辅助评分;评估报告生成;按预设目标自动生成 IEP 干预方案;康复进度追踪与趋势 | 不做教案细化(→ 教学训练);不做文献信效度科研分析(→ 科研助手) |
| 3 | **科研助手 Agent** `agent-research` | 特教科研支持 | 特教文献检索与研读;实证研究数据分析;课题申报书撰写辅助;量表信效度计算(Cronbach α / 因子分析等);个人知识库 | 不做临床评估(→ 评估干预);不做日常问答(→ 通用问答) |
| 4 | **智能教具 Agent** `agent-aids` | 教具推荐与数字化教具生成 | 特殊教具推荐与使用指导;数字化教具(文生图/文生绘本/文生视频/文生音频);AR/VR 交互游戏;特教教学资源库(手语、盲文、教材、课表、向量化资源) | 不做教学设计(→ 教学训练);不做素材的教研分析(→ 科研助手) |
| 5 | **通用问答 Agent** `agent-qa` | 通用 AI 问答 | 师生家长通用问答;政策解读;特教常识问答;转人工/转专业 Agent 兜底路由 | 不做需写库的业务操作(路由至对应业务 Agent) |

### 1.3 数据与知识底座(横切,非 Agent)

支撑五个 Agent 的共享能力层,**不直接面向用户**:

- **AI 网关**(`ai-gateway`):统一 LLM/多模态模型接口,封装「脱敏 → 调模型 → 还原」三步;可插拔(Mock / OpenAI / 私有化)。
- **脱敏层**(`anonymizer` + `image-anon`):文本 PII 占位符替换 + 图像本地打码;出网必经,失败硬阻断。
- **RAG 检索**(`rag`):8 品类特教知识库的切片、向量化、召回、重排。
- **向量库**(Milvus):知识向量、资源向量存储与近邻检索。
- **合规审计**(`compliance`):全链路审计日志、知情同意、数据生命周期。
- **对象存储**(`storage`):报告 PDF、多模态素材、教具产物(MinIO / Noop 本地)。

### 1.4 与现状的关系

现有 SELLM 模块化单体(`com.sellm.*`:org/clazz/user/parent/child/scale/assessment/report/iep/rag/aigateway/anonymizer/multimodal/storage/security/common)作为**评估干预 Agent 的业务内核**整体保留,演进为独立服务;其 `aigateway`/`anonymizer`/`rag`/`storage` 等共享能力**上提为平台底座**,供五个 Agent 复用。其余四个 Agent 为新建。

### 1.5 第一阶段范围与 YAGNI

- **在范围**:平台骨架(网关 + 注册发现 + 消息总线 + 底座)、评估干预 Agent(现有演进)、其余四 Agent 的服务脚手架 + 1 条最小可用链路。
- **不在第一阶段**:模型私有化部署与领域微调(架构预留切换,不实现);AR/VR 实时渲染引擎(先出文生图/绘本最小链路);Kafka 级高吞吐(RabbitMQ 够用);跨机构联邦学习。

---

## 2. 用户角色与权限模型

### 2.1 四级角色

| 角色 | 代码 | 定位 | 数据可见范围 |
|---|---|---|---|
| 超级管理者 | `SUPER_ADMIN` | 平台运营方(高校) | 跨机构、全平台 |
| 机构/学校管理员 | `MANAGER` | 单机构管理者 | 限本机构 |
| 特教教师/康复师 | `TEACHER` | 一线评估干预执行者 | 限本机构(可细化到本班级) |
| 家长 | `PARENT` | 残障儿童监护人 | 限自己孩子 |

> 说明:需求中"管理员/特教教师/康复师/家长"四类,特教教师与康复师在权限上同属 `TEACHER`(业务能力一致),通过 `userType` 子字段(教师/康复师)区分展示与统计,不增加权限层级。

### 2.2 角色 — Agent 能力矩阵

| Agent / 能力 | SUPER_ADMIN | MANAGER | TEACHER | PARENT |
|---|---|---|---|---|
| 平台管理(机构/量表库/合规) | ✅ 全量 | 本机构班级/老师/家长(只读家长) | ❌ | ❌ |
| 教学训练 Agent | 查看 | 查看本机构 | ✅ 备课/课件/教学设计 | ❌ |
| 评估干预 Agent | 查看 | 查看本机构 | ✅ 评估/报告/IEP/进度 | 设家庭 IEP 目标 + 查看本孩子 |
| 科研助手 Agent | ✅ | 本机构 | ✅ 文献/信效度/课题/知识库 | ❌ |
| 智能教具 Agent | ✅ | 本机构 | ✅ 推荐/生成/资源库 | 轻量查看(家庭教具建议) |
| 通用问答 Agent | ✅ | ✅ | ✅ | ✅(家长向问答) |

### 2.3 两道权限闸门(全平台强制)

1. **端点级 RBAC**:API Gateway + 各服务 `SecurityConfig` 有序 `requestMatchers`,更具体路径排在通配 `/api/**` 之前。统一 JWT,网关校验签名 + 注入用户上下文(userId/role/orgId)到下游请求头。
2. **行级数据权限**:`AccessGuard` 单点校验。`checkChildAccess(principal, child)`:SUPER_ADMIN 跨机构、MANAGER/TEACHER 限本机构、PARENT 限自己孩子(经 `child.guardianUserId`)。凡涉及具体儿童/班级/机构的读写都必须调用。Python 智能层不直连业务库,经 Java 服务取数,权限在 Java 侧统一把控。

### 2.4 家长注册与审核

公开注册填写:姓名、账号、密码、儿童姓名、所在班级的老师/康复师、所在学校、残障类型。审核流转:注册信息发给所填老师/康复师审核;**审核通过后家长才能登录**(PENDING 不能登录)。

---

## 3. 技术栈选型

### 3.1 总览(以现有构建文件为基线演进)

| 层 | 技术 | 版本 | 说明 |
|---|---|---|---|
| Web 管理端 | Vue 3 + Vite 5 + Element Plus + Pinia + Vue Router | 现有 | ECharts 图表、Sass 样式 |
| 小程序端 | uni-app(Vue3 + Pinia)编译微信 | 新增 | 家长轻交互 |
| API 网关 | Spring Cloud Gateway | 新增 | 鉴权/路由/限流/熔断 |
| 业务服务层 | Java 17 + Spring Boot 3.2.5 + MyBatis + Spring Security(JWT) | 现有 | 用户/权限/数据/流程/PII 加密 |
| AI 智能层 | Python 3.11 + FastAPI + LangGraph + LlamaIndex | 新增 | LLM 编排 / RAG / 多模态 |
| 服务注册/配置 | Nacos | 新增 | 服务发现 + 配置中心 |
| 消息总线 | RabbitMQ | 新增 | Agent 间异步事件、长任务 |
| 业务数据库 | MySQL 8(prod)/ H2(dev 文件库、test 内存库) | 现有 | |
| 缓存/会话 | Redis 7 | 新增 | 网关限流、会话、热点缓存 |
| 向量库 | Milvus 2.x | 新增 | 知识/资源向量检索 |
| 对象存储 | MinIO(可切 Noop 本地) | 现有 | 报告/素材/教具产物 |
| 容器编排 | docker-compose(dev)/ K8s(prod 预留) | 新增 | |

> `.claude/CLAUDE.md` 中"Java 21 + PostgreSQL"与实际不符,以 `pom.xml`/`application.yml` 为准:**Java 17 + MySQL/H2**。

### 3.2 Java 业务服务约定(沿用现有)

- 根包 `com.sellm.*`,分层 `controller → *AppService(行级权限+事务) → *Service(领域) → *Repository → *Mapper(+ resources/mybatis/*.xml)`。
- MyBatis 风格:Mapper 收发 `Map<String,Object>`;XML resultMap 把 snake_case 映射 camelCase;insert 用 `useGeneratedKeys` 写回 id;Repository 手动类型转换。新增记录表照"实体 + Mapper + XML + Repository"四件套。
- 统一返回 `common/result` 的 `Result<T>` 信封;异常走 `common/exception`。
- 构建 Maven(`./mvnw`)。

### 3.3 Python 智能层约定(新增)

- FastAPI + Pydantic 模型;LangGraph 做 Agent 内多步编排;LlamaIndex 做 RAG 管线。
- **不直连业务数据库**:取数经 Java 服务 gRPC/REST;只接触脱敏后数据。
- 外部模型/存储为**可切换适配器,默认 Mock 不外联**;真实 HTTP 客户端强制 HTTP/1.1。
- 依赖锁定版本(`requirements.txt` / `poetry.lock`),优先成熟活跃维护包。
- 单测 pytest;LLM 调用走 Mock 适配器,验证结构合规而非精确文本。

### 3.4 AI 适配器红线(全平台继承现有约定)

所有外部模型/存储/脱敏服务都是**可切换适配器,默认不外联**:`AiModel`(mock/openai)、`MultimodalModel`(mock/openai vision)、`ObjectStorage`(noop/minio)、`ImageAnonymizer`(noop/http)。装配逻辑在各自 `*Config`,按 `sellm.*.provider` + 是否配 key/endpoint 决定真实实现 vs Mock/Noop。新增适配器时把"发请求"抽成 `protected send(...)` 便于测试子类化注入假响应。

---

## 4. 多 Agent 通信架构

### 4.1 拓扑

```
        [Web 管理端 Vue3]        [微信小程序 uni-app]
              └──────────┬──────────────┘
                  API Gateway (Spring Cloud Gateway)
                  · JWT 校验 · 路由 · 限流(Redis) · 熔断
                         │  注入 X-User-Id / X-User-Role / X-Org-Id
        ┌────────────────┼─────────────────────────────┐
        │          Nacos 注册发现 + 配置中心             │
        │                                               │
   ┌────┴────┬─────────┬──────────┬──────────┬─────────┐
 教学训练   评估干预   科研助手    智能教具    通用问答
 agent-     agent-     agent-      agent-      agent-
 teaching   assessment research    aids        qa
   │(Java)    │(Java)    │(Java+Py)  │(Java+Py)   │(Py)
   └────┬─────┴────┬─────┴─────┬────┴─────┬─────┘
        │          │           │          │
        │   ┌──────┴───────────┴──────┐   │
        └───┤   RabbitMQ 事件总线      ├───┘   ← 异步:长任务/跨Agent事件
            └──────────┬───────────────┘
                       │
        ┌──────────────┴──────────────────────┐
        │      数据与知识底座(共享)            │
        │ ai-gateway · anonymizer · rag ·       │
        │ compliance · storage                  │
        └──────────────┬──────────────────────┘
                       │
   MySQL8 · Redis7 · Milvus · MinIO · RabbitMQ
```

> 图中 `(Java)`/`(Py)`/`(Java+Py)` 标注**重心**而非全部:每个 Agent 都有一层 Java 业务服务(承载鉴权/落库/行级权限/对外 REST),`agent-teaching`/`agent-assessment` 重业务故主体 Java;`agent-research`/`agent-aids` 业务与智能并重;`agent-qa` 智能层(意图路由/RAG)为主、Java 业务面较薄(仅会话持久化与鉴权,见 §6.8)。Python 智能层一律不直连业务库(§2.3、§3.3)。

### 4.2 通信方式选型

| 场景 | 方式 | 说明 |
|---|---|---|
| 端 → 平台 | HTTPS REST(经网关) | 统一入口,JWT 鉴权 |
| 网关 → Agent 业务服务 | REST(负载均衡,经 Nacos) | 同步请求-响应 |
| Java 业务服务 ↔ Python 智能层 | **gRPC**(同进程域内强类型) + REST 兜底 | 智能层取数/回写经此;强类型 protobuf |
| Agent ↔ Agent(同步协作) | OpenFeign + Nacos | 如评估干预调教学训练生成教案 |
| Agent ↔ Agent(异步/长任务) | **RabbitMQ** 事件 | 文生视频、批量评估、进度通知 |
| 跨 Agent 编排(多步) | **Saga 编排**(Java 侧协调器)+ LangGraph(Agent 内步骤) | 见 4.4 |

### 4.3 Agent 编排:两层职责

- **平台级编排(Java,Saga 协调器)**:跨 Agent 的业务流程(如"评估→生成报告→推荐教案→推荐教具"链),用 Saga 模式串联,每步可补偿;失败回滚不留脏数据。状态机持久化到 MySQL(`agent_orchestration` 表)。
- **Agent 级编排(Python,LangGraph)**:单个 Agent 内的多步 LLM 推理(如"检索→草拟→自检→精修"),用 LangGraph 状态图编排;不跨 Agent。

### 4.4 通用问答 Agent 的意图路由

通用问答 Agent 作为**统一入口兜底**,对用户自然语言意图分类后路由:

```
用户提问 → agent-qa 意图分类(LLM 分类器)
  ├─ 通用/政策/常识 → 本地 RAG 直接回答
  ├─ "帮我评估..." → 路由 agent-assessment(返回深链)
  ├─ "帮我备课..." → 路由 agent-teaching
  ├─ "推荐教具..." → 路由 agent-aids
  └─ "查文献/算信效度" → 路由 agent-research
```

路由只返回"建议跳转 + 深链",**不代替用户执行写操作**(写操作仍需用户在对应 Agent 页面确认,守住"AI 只产草案"红线)。

### 4.5 事件契约(RabbitMQ)

- Exchange:`sellm.agent.events`(topic 类型)。
- Routing key 规范:`{agent}.{entity}.{action}`,如 `assessment.report.finalized`、`aids.video.generated`。
- 消息体:`{ eventId, occurredAt, actorUserId, orgId, payload }`,**payload 内不含明文 PII**(只传 id + 脱敏字段)。
- 消费幂等:`eventId` 去重(Redis `SETNX`);失败进死信队列 `sellm.agent.dlq`。

### 4.6 服务降级与隔离

- 任一 Agent 不可用不影响其他 Agent(网关熔断 + 默认响应)。
- AI 智能层不可用时,业务服务保留已落库数据,提示"AI 草稿生成失败,可重试或手动撰写"。
- 底座(脱敏/向量库)不可用:脱敏失败硬阻断;向量库失败 RAG 降级为"无检索增强"提示,不阻断主流程。

---

## 5. 前后端接口规范

### 5.1 通用约定

- **协议**:HTTPS;端 → 平台统一走 API Gateway。
- **路由前缀**:`/api/{agent}/...`,如 `/api/assessment/...`、`/api/teaching/...`、`/api/aids/...`、`/api/research/...`、`/api/qa/...`;平台管理类 `/api/admin/...`、认证类 `/api/auth/...`。
- **统一响应信封** `Result<T>`:

```json
{ "code": 0, "message": "ok", "data": { } }
```

`code=0` 成功;非 0 为业务/系统错误码。前端断言 `data` 字段(测试断言 `$.data...`)。

- **认证**:JWT `Authorization: Bearer <token>`;网关校验后注入 `X-User-Id`/`X-User-Role`/`X-Org-Id` 透传下游。
- **分页**:请求 `?page=1&size=20`;响应 `data: { list, total, page, size }`。
- **时间**:ISO-8601 UTC 字符串;前端本地化展示。
- **幂等**:写操作可带 `Idempotency-Key` 头(长任务必带)。

### 5.2 错误码分段

| 段 | 含义 |
|---|---|
| 0 | 成功 |
| 1xxx | 通用(参数/校验/未找到) |
| 2xxx | 认证授权(401/403/token 失效) |
| 3xxx | 业务规则(状态机不允许/重复操作) |
| 4xxx | AI/脱敏(脱敏失败硬阻断、模型超时) |
| 5xxx | 系统(依赖不可用、内部错误) |

### 5.3 长任务(异步)接口规范

文生视频、批量评估、多模态识别等耗时操作:

```
POST /api/aids/videos            → 202 Accepted, data: { taskId }
GET  /api/aids/tasks/{taskId}    → data: { status: PENDING|RUNNING|SUCCESS|FAILED, result?, error? }
```

可选 SSE / WebSocket 推进度;小程序端轮询 `GET tasks/{taskId}`。

### 5.4 各 Agent 代表性端点(节选,完整以各 Agent 子文档/OpenAPI 为准)

**认证与管理**
- `POST /api/auth/login` `POST /api/auth/register`(家长公开注册)`PUT /api/auth/password`
- `POST /api/admin/orgs`(超管建机构)`GET /api/admin/orgs`
- `*/api/admin/classes`(管理员班级 CRUD)`GET /api/admin/parents`(管理员看本机构家长)
- `*/api/admin/scales`(超管量表库 CRUD)

**评估干预 Agent**(现有演进)
- `GET /api/assessment/children/{id}/recommended-scales`(按障碍类型推荐量表)
- `POST /api/assessment/assessments`(选量表+评分)`GET /api/assessment/assessments/{id}`
- `POST /api/assessment/assessments/{id}/multimodal`(多模态识别→指标评分建议,202)
- `POST /api/assessment/reports`(生成报告草案 DRAFT)`PUT /api/assessment/reports/{id}`(编辑)`POST .../finalize`(定稿)`GET .../pdf`(仅 FINALIZED)
- `POST /api/assessment/ieps` / `finalize` / `pdf`;家庭 IEP `POST /api/assessment/family-ieps`
- `GET /api/assessment/children/{id}/progress`(进度趋势)

**教学训练 Agent**
- `POST /api/teaching/lesson-plans`(由 IEP/障碍类型生成分层教案草案,202)
- `POST /api/teaching/courseware`(适配课件生成,202)
- `GET /api/teaching/lesson-plans/{id}` `PUT .../{id}`(编辑定稿)

**科研助手 Agent**
- `POST /api/research/literature/search`(文献检索)
- `POST /api/research/reliability`(信效度计算:Cronbach α / 因子分析)
- `POST /api/research/proposals`(课题书撰写草案)
- `*/api/research/knowledge-base`(个人知识库 CRUD + 检索)

**智能教具 Agent**
- `GET /api/aids/recommendations?disorderType=ASD`(教具推荐)
- `POST /api/aids/images|picturebooks|videos|audios`(文生素材,202)
- `GET /api/aids/resources?type=sign-language|braille`(资源库检索)

**通用问答 Agent**
- `POST /api/qa/ask`(问答,data 含 answer + 可选 routeTo 深链)
- `GET /api/qa/conversations/{id}`(会话历史)

### 5.5 接口文档产出

每个 Agent 服务暴露 OpenAPI 3.0(springdoc / FastAPI 自动生成);网关聚合为统一文档门户。**所有 API 变更必须更新 OpenAPI 并同步前端 api 封装**(`frontend/src/api/*`)。

---

## 6. 数据库表结构

### 6.1 设计原则

- **每个 Agent 拥有独立逻辑库**(prod 可物理分库,dev/test 同库不同表前缀),不跨库直连;跨 Agent 取数走服务接口。
- PII 字段(姓名/身份证/联系方式)经 `FieldCipher`(AES-GCM)**加密落库**,领域对象持明文,Repository 负责加解密。
- 所有表含 `id BIGINT PK AUTO_INCREMENT`、`created_at`、`updated_at`、`created_by`、软删除 `deleted TINYINT`。
- schema 变更先改 `schema.sql`;dev H2 文件库 `schema.sql + seed-dev.sql` 每次启动幂等执行。

### 6.2 平台共享域(账户/组织/合规)

| 表 | 关键字段 | 说明 |
|---|---|---|
| `sys_user` | id, username, password_hash, role, user_type(教师/康复师), org_id, status(PENDING/ACTIVE/REJECTED), real_name🔒 | 四级角色账户 |
| `organization` | id, name, disorder_types(JSON 多选), province, city, manager_user_id | 机构(扩字段) |
| `class` | id, name, org_id, disorder_types(JSON) | 班级 |
| `class_teacher` | class_id, teacher_user_id | 班级-老师 多对多 |
| `parent_register_audit` | id, parent_user_id, child_name🔒, target_teacher_id, school, disorder_type, status | 家长注册审核 |
| `audit_log` | id, actor_user_id, action, target_type, target_id, detail(JSON), created_at | 合规审计(全链路) |
| `consent_record` | id, subject_child_id, scope, granted_by, granted_at, revoked_at | 知情同意 |
| `ai_generation_log` | id, agent, prompt_meta, model, ai_draft, human_final, created_by | AI 原稿 vs 人工终稿(回流语料) |

### 6.3 儿童档案域(评估干预 Agent,现有大扩展)

| 表 | 关键字段 | 说明 |
|---|---|---|
| `child` | id, name🔒, disorder_type, guardian_user_id, class_id, baseline_assessment_id, latest_assessment_id, annual_iep_id, monthly_goal, next_review_at, iep_due_at | 儿童档案 |
| `child_log` | id, child_id, type(课堂追踪/家校沟通/阶段复盘), content, author_id, log_at | 各类记录(分 type) |

### 6.4 量表与评估域(评估干预 Agent,现有)

| 表 | 关键字段 | 说明 |
|---|---|---|
| `scale` | id, name, disorder_type, version | 量表定义(8 品类) |
| `scale_item` | id, scale_id, stem, options(JSON), dimension | 题目 |
| `scoring_rule` | id, scale_id, segment, threshold, interpretation | 计分规则/分段 |
| `assessment` | id, child_id, scale_id, answers(JSON), score, assessor_id, assessed_at | 评估记录 |
| `assessment_media` | id, assessment_id, storage_key, media_type, anonymized | 多模态素材(脱敏标记) |
| `report` | id, assessment_id, ai_draft, content, status(DRAFT/FINALIZED), finalized_by | 评估报告 |
| `iep` | id, child_id, type(SCHOOL/FAMILY), ai_draft, content, status | IEP 计划 |
| `iep_goal` | id, iep_id, term(LONG/SHORT), goal, measure, status | IEP 目标 |
| `iep_version` | id, iep_id, version_no, snapshot(JSON) | 版本追溯 |
| `progress_record` | id, iep_goal_id, child_id, achievement, performance, recorded_by, recorded_at | 进度记录(挂短期目标) |

### 6.5 教学训练 Agent 域

| 表 | 关键字段 | 说明 |
|---|---|---|
| `lesson_plan` | id, child_id?, class_id?, source_iep_id, scene(HOME/SCHOOL/ORG), mode(ONE_ON_ONE/GROUP), ai_draft, content, status | 分层教案 |
| `courseware` | id, lesson_plan_id, disorder_type, storage_key, format, status | 适配课件产物 |

### 6.6 科研助手 Agent 域

| 表 | 关键字段 | 说明 |
|---|---|---|
| `literature` | id, owner_id, title, authors, source, doi, summary, vector_id | 文献(向量化) |
| `reliability_calc` | id, owner_id, dataset(JSON), method, result(JSON) | 信效度计算记录 |
| `research_proposal` | id, owner_id, topic, ai_draft, content, status | 课题申报书 |
| `personal_kb_doc` | id, owner_id, title, storage_key, vector_collection | 个人知识库文档 |

### 6.7 智能教具 Agent 域

| 表 | 关键字段 | 说明 |
|---|---|---|
| `teaching_aid` | id, name, disorder_types(JSON), category, usage_guide | 教具(推荐用) |
| `generated_asset` | id, owner_id, type(IMAGE/PICTUREBOOK/VIDEO/AUDIO), prompt, storage_key, task_id, status | 文生素材产物 |
| `resource_library` | id, type(SIGN_LANGUAGE/BRAILLE/TEXTBOOK/SCHEDULE), title, storage_key, vector_id | 资源库 |

### 6.8 通用问答 Agent 域

| 表 | 关键字段 | 说明 |
|---|---|---|
| `qa_conversation` | id, user_id, title, created_at | 会话 |
| `qa_message` | id, conversation_id, role(USER/ASSISTANT), content, route_to, sources(JSON) | 消息 + 路由 + 引用来源 |

### 6.9 编排域(平台)

| 表 | 关键字段 | 说明 |
|---|---|---|
| `agent_orchestration` | id, saga_type, status, current_step, context(JSON), compensations(JSON) | Saga 状态机持久化 |

---

## 7. 向量库设计

### 7.1 选型与定位

- **Milvus 2.x** 独立部署,承载平台所有向量检索;dev 环境可用 Milvus Lite / 嵌入式替代降低门槛。
- 向量库**不存 PII**:只存知识/资源/文献的语义向量 + 元数据指针(指向 MinIO/MySQL 的原文 key)。
- 嵌入模型经 `ai-gateway` 统一调用(可插拔,默认 Mock 返回确定性伪向量供测试)。

### 7.2 Collection 划分(按用途隔离,便于权限与召回精度)

| Collection | 用途 | 归属 Agent | 元数据字段 |
|---|---|---|---|
| `kb_special_edu` | 8 品类特教知识库(评估解读规则、常模、干预策略、IEP 范例) | 评估干预/教学训练 | disorder_type, doc_type, source, chunk_id |
| `kb_policy` | 政策法规、特教常识 | 通用问答 | region, effective_date, source |
| `kb_resource` | 资源库(手语/盲文/教材/课表)语义索引 | 智能教具 | resource_type, disorder_type |
| `kb_literature` | 科研文献 | 科研助手 | owner_id(个人隔离), year, journal |
| `kb_personal_{userId}` | 个人知识库(逻辑隔离:partition by owner_id) | 科研助手 | owner_id, doc_id |

### 7.3 向量参数

- 维度:随嵌入模型(默认占位 768 / 1024;切真实模型时统一);**同一 Collection 维度固定**,切模型需重建。
- 索引:`HNSW`(M=16, efConstruction=200),度量 `COSINE`。
- 检索:Top-K(默认 5)+ 元数据过滤(`disorder_type`/`owner_id`)+ 可选 rerank(重排模型经 ai-gateway)。

### 7.4 RAG 管线(LlamaIndex,Python 智能层)

```
原始文档 → 清洗 → 切片(语义分块,重叠 token) → 嵌入(ai-gateway)
  → 写入 Milvus(带元数据) ───────────────────────────┐
                                                       │
用户查询 → 嵌入 → Milvus Top-K(元数据过滤) → rerank   │
  → 拼接上下文 → LLM 生成(带 source 引用) → 返回 ◄─────┘
```

### 7.5 权限与隔离

- 个人知识库(`kb_personal_*` / `kb_literature` owner_id)**严格按 owner 过滤**,检索时强制注入 `owner_id == 当前用户` 的元数据过滤,杜绝越权召回。
- 知识库写入(管理员/超管维护公共库;老师维护个人库)经业务服务鉴权后再触发向量化。

### 7.6 知识库治理

- 来源可溯:每个 chunk 记 source + chunk_id,RAG 回答带引用,便于人工核验。
- 版本与失效:政策类带 `effective_date`,过期内容降权或标注。
- 数据回流:老师对 AI 草案的修改脱敏后沉淀为专属语料(数据飞轮),经审核入 `kb_special_edu`。

---

## 8. 小程序适配约束

### 8.1 定位

微信小程序为**家长轻量终端**,uni-app(Vue3 + Pinia)编译微信端。家长能力:查看本孩子档案/报告/IEP、设家庭 IEP 目标、查看进度趋势、家庭教具建议、通用问答。**不承载重管理交互**(管理/评估/备课等在 Web 端)。

### 8.2 技术与合规约束

- **包体积**:主包 ≤ 2MB,总包 ≤ 20MB(微信限制);大资源走分包加载 + CDN/MinIO 外链。
- **网络**:仅 `https` 合法域名(微信后台配置);长任务用轮询 `GET tasks/{taskId}`,不长连占用。
- **登录**:微信 `wx.login` 取 code → 后端换 openid → 绑定已审核家长账户;首次需绑定流程。
- **隐私合规**:遵守微信《小程序隐私协议》;采集任何用户信息前弹隐私授权;**不在小程序端缓存明文 PII**,儿童敏感信息按需拉取、用后不持久化。
- **支付**:第一阶段无支付。

### 8.3 交互与性能约束

- 页面首屏 ≤ 3 个核心模块卡片;长列表分页/虚拟列表。
- 多模态素材(报告 PDF、教具视频)用微信原生组件预览或外链打开,不在小程序内重渲染。
- 弱网降级:接口失败给明确重试入口;关键数据本地短缓存(非 PII)。
- 适配:rpx 响应式;深色模式跟随系统;无障碍(家长群体含特殊需求)文字可放大。

### 8.4 与 Web 端能力差异

| 能力 | Web 管理端 | 小程序家长端 |
|---|---|---|
| 评估/报告/IEP 编辑定稿 | ✅ | ❌(只读已定稿) |
| 设家庭 IEP 目标 | — | ✅ |
| 教具生成 | ✅ 全量 | ❌(看家庭建议) |
| 通用问答 | ✅ | ✅(家长向) |
| 管理/班级/量表库 | ✅ | ❌ |

---

## 9. 安全规范

### 9.1 三条 AI/隐私红线(全平台最高约束)

1. **数据出网必经脱敏层**:任何发往外部大模型/多模态 API 的数据,身份信息(姓名/学校/身份证)必须先本地替换为占位符(`[儿童N]`/`[学校N]`);图像出网前必过 `ImageAnonymizer` 打码。脱敏校验不通过则**硬阻断,绝不发送**。
2. **AI 只产草案,人工定稿**:报告/IEP/家庭IEP/教案/课题书均为 DRAFT,专业人员编辑后 finalize;PDF/产物下载仅对 FINALIZED。AI 不直接对儿童下结论。多模态只产"指标评分建议",老师确认后才计分。
3. **敏感数据自主可控**:残障儿童(未成年 + 敏感个人信息双重高敏)原始身份信息 `FieldCipher`(AES-GCM)加密留存本地,永不出网。

### 9.2 认证授权

- 统一 JWT(`SELLM_JWT_SECRET`);网关校验签名 + 过期;下游信任网关注入的用户上下文头。
- 端点级 RBAC(有序 requestMatchers,具体路径先于通配)+ 行级 AccessGuard 单点校验。
- Python 智能层无业务库直连、无独立鉴权入口,只能经 Java 服务调用(内网 + mTLS 预留)。

### 9.3 数据安全

- PII 加密落库(AES-GCM,密钥 `SELLM_AES_KEY` 走环境变量,不入库不入码)。
- 传输全程 HTTPS;内部服务间 prod 启用 mTLS(预留)。
- 对象存储私有桶 + 预签名 URL 临时授权;下载链接短时效。
- 向量库不存 PII(见 §7.1)。

### 9.4 合规框架

- **全链路审计**(`audit_log`):谁在何时对何对象做了什么(创建/编辑/审核/定稿/下载/AI 调用),合规可追溯。
- **知情同意**(`consent_record`):采集残障儿童数据前留存监护人同意;可撤销。
- **数据生命周期**:留存期、删除/匿名化流程;支持数据主体权利请求(查询/删除)。
- **未成年人保护**:最小必要采集;PII 访问最小授权。

### 9.5 配置与密钥

- 密钥/外部服务全走 `${ENV:default}` 环境变量,清单见根目录 `.env.example`(`SELLM_AES_KEY`/`SELLM_JWT_SECRET`/`SELLM_AI_*`/`SELLM_MULTIMODAL_*`/`SELLM_MINIO_*`/`SELLM_IMAGE_ANON_*`/`SELLM_PDF_FONT_PATH` 等),新增 Agent 的密钥同步扩充。
- dev profile 内置开发默认值;真值不入库。
- 新增依赖锁定版本,不引入未经安全审查的第三方库。

### 9.6 网关防护

- 限流(Redis 令牌桶,按用户/IP)、熔断、防重放(Idempotency-Key)、防注入(参数校验前置)。
- 长任务防滥用:任务配额 + 速率限制(文生视频等高成本操作)。

---

## 10. 测试标准

### 10.1 分层测试策略

| 层 | 范围 | 工具 | 重点 |
|---|---|---|---|
| 单元测试 | 量表计分(各分段/边界)、脱敏层(PII 必被替换+可还原)、信效度算法、权限 AccessGuard | JUnit5(Java)/ pytest(Py) | **正确性与合规命门,高覆盖** |
| API/集成 | 各 Agent 端点真实 HTTP、跨 Agent Saga 链路 | `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")` + MockMvc / httpx(Py) | 信封 `$.data` 断言 |
| AI 输出验证 | 不做精确断言;验证结构合规(字段齐全)+ 关键约束(输出不含被脱敏 PII) | Mock 模型适配器 | 可重复、低成本、不外联 |
| 端到端联调 | 每阶段 curl 走真实 HTTP 全链路 | curl + `frontend/INTEGRATION.md` 累积 | dev H2 + 种子 |
| 前端构建 | 改完前端必跑,验证可编译 | `npm run build` | |

### 10.2 测试约定(沿用现有)

- Java API 测试:`@BeforeEach` 用 `jdbc.update(...)` 直接种数据;取 token 用 `AuthTestSupport.registerAndLogin(...)`;响应断言 `$.data...`;方法名可中文。
- Python:LLM/多模态/存储/脱敏全走 Mock/Noop 适配器,不真连网;适配器把"发请求"抽成可覆写 `send(...)` 便于注入假响应。
- **偶发**:并行 surefire 启动时 H2 内存库偶现 "database has been closed" 瞬时错误,单独重跑通过即 flake。

### 10.3 质量门禁(每阶段 / 合并前)

1. 后端全量 `./mvnw test` 绿;Python `pytest` 绿。
2. 前端 `npm run build` 成功。
3. 新增功能必带对应单测;不得删除/绕过现有测试。
4. 端到端 curl 联调结果累积到 `frontend/INTEGRATION.md`。
5. 脱敏/权限相关变更必须有"PII 不外泄""越权被拒"的针对性用例。
6. 每次代码/配置变更追加记录到 `.claude/CLAUDE_CHANGES.md`(项目强制规则)。

### 10.4 AI 质量评估(非自动化)

AI 输出质量靠样板校人工评估闭环把关(3-5 所样板校真实跑通);老师对草案的修改作为质量信号回流。

---

## 11. 分期实现路线图

| 阶段 | 内容 | 依赖 | 备注 |
|---|---|---|---|
| **P0 平台地基** | API 网关 + Nacos + RabbitMQ + 底座上提(ai-gateway/anonymizer/rag/storage 抽为共享)+ docker-compose 编排 + 多 Agent 服务脚手架 | — | 不破坏现有评估干预功能 |
| **P1 评估干预 Agent** | 现有 SELLM 演进为独立服务接入网关;v2 需求(组织/班级/量表库/儿童档案/真实 AI/多模态)按现有 A–G 计划推进 | P0 | 已有大量实现 |
| **P2 通用问答 Agent** | RAG 知识库 + 意图路由(平台统一入口兜底) | P0 | Python 智能层首发 |
| **P3 教学训练 Agent** | IEP→分层教案、适配课件;与评估干预 Saga 联动 | P1 | |
| **P4 智能教具 Agent** | 教具推荐 + 文生图/绘本最小链路 + 资源库;文生视频/AR/VR 后续 | P0 | 长任务异步范式 |
| **P5 科研助手 Agent** | 文献检索 + 信效度计算 + 个人知识库 + 课题书 | P0 | |
| **P6 小程序家长端** | uni-app 编译微信;家长查看/家庭IEP/问答 | P1 | |

> 每个 Agent 走独立"设计→计划→实现"循环,本平台 Spec 为纲领;各 Agent 实现前可出子 Spec 细化。

---

## 12. 术语表

| 术语 | 含义 |
|---|---|
| Agent | 职责单一、可独立部署的业务智能体(本平台五个) |
| 底座 | 数据与知识共享能力层(AI网关/脱敏/RAG/向量库/合规/存储) |
| IEP | 个别化教育计划(Individualized Education Program) |
| RAG | 检索增强生成(Retrieval-Augmented Generation) |
| Saga | 分布式长事务编排模式(每步可补偿) |
| 脱敏/还原 | 出网前 PII 替换为占位符,返回后本地还原 |
| 8 类障碍 | 孤独症谱系/发育迟缓/智力障碍/语言障碍/感统/脑瘫/ADHD/听视障 |
| 草案→定稿 | AI 产 DRAFT,人工编辑后 FINALIZED,下载仅对定稿 |

---

> **本 Spec 为平台纲领文档。** 任何 Agent 的详细实现先经其子 Spec/计划细化;所有变更遵守三条 AI/隐私红线与项目强制规则(`.claude/CLAUDE.md`)。
