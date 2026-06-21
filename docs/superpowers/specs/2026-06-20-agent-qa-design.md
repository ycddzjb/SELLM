# 通用问答 Agent(agent-qa)设计文档

> 文档日期:2026-06-20
> 状态:设计已确认,待用户复核
> 隶属:特殊教育垂直大模型平台 · 第 5 个 Agent · Python 智能层首发
> 平台 Spec:`docs/spec/special_edu_llm_spec.md`(§4.4 意图路由 / §6.8 qa 表 / §7.2 kb_policy)

## 1. 定位与范围

通用问答 Agent 是平台五大 Agent 的第 5 个,作为**统一问答入口与兜底**:回答师生家长的通用问答、政策解读、特教常识;对涉及具体业务(评估/备课/教具/科研)的提问,**返回深链路由建议,不代替用户执行**。本 Agent 是 **Python 智能层首次真实落地**(此前 P0 仅空壳)。

### 1.1 第一版范围(mock 优先)

- **在范围**:Java agent-qa 业务服务(鉴权 + 会话持久化 + 意图路由 + 脱敏编排)、Python 智能层(意图分类后的 RAG 问答管线)、Java↔Python REST 协作、端到端骨架跑通。
- **mock 不外联**:嵌入模型、LLM、向量检索默认 mock/noop,真实 Milvus/嵌入/LLM 为可切换适配器但**默认不启用**。
- **不在第一版(YAGNI)**:真实知识库灌库与向量化、多轮对话上下文记忆(首版单轮问答 + 会话历史记录,但不把历史喂回 LLM)、流式输出(SSE)、LLM 意图分类(首版规则分类器)、跨 Agent 实际跳转执行(只给深链)。

## 2. 架构与调用链

```
用户 → 网关(JWT 验签 + 注入 X-User-* + Redis 限流)
        → Java agent-qa: POST /api/qa/ask
  ┌─ Java 侧(sellm-agent-qa)────────────────────────────────┐
  │ 1. 解析 X-User-Id;AccessGuard 校验会话归属本人          │
  │ 2. 创建/取会话(qa_conversation);写 USER 消息(qa_message)│
  │ 3. IntentClassifier(规则)对问题分类                      │
  │ 4a. 业务类意图 → 直接构造 routeTo + 深链(不调 Python)     │
  │ 4b. 通用/政策/常识 → Anonymizer 脱敏问题 → REST 调 Python  │
  │ 5. Python 返回 answer + sources(占位符)→ Java 还原 PII   │
  │ 6. 写 ASSISTANT 消息(含 route_to/sources)→ Result<QaAnswer>│
  └────────────────────────────────────────────────────────┘
                          │ REST(仅 4b)
  ┌─ Python 侧(ai-smart-layer)─────────────────────────────┐
  │ POST /v1/agents/qa/invoke {question(脱敏), top_k}        │
  │   RAG 管线(LlamaIndex 结构):                            │
  │     mock 嵌入 → mock 向量检索(kb_policy)→ 拼上下文       │
  │     → mock LLM 生成带引用答案 → {answer, sources}        │
  └────────────────────────────────────────────────────────┘
```

### 2.1 职责边界(继承平台架构)

- **Java agent-qa**:鉴权、会话持久化、意图路由决策、脱敏/还原编排。**持 qa 业务库**。
- **Python 智能层**:意图分类后的 RAG 问答生成。**不直连业务库、不做鉴权、只接收脱敏后文本**。
- 业务类意图的"跳转"只是给前端深链建议;**写操作仍需用户在对应 Agent 页面确认**(守 AI 只产草案红线)。

## 3. 组件设计(单一职责)

### 3.1 Java 侧(sellm-agent-qa,根包 com.sellm.qa)

| 组件 | 职责 | 依赖 |
|---|---|---|
| `QaController` | REST 端点(ask / 会话历史 / 会话列表);端点级 RBAC | QaAppService |
| `QaAppService` | 行级权限 + 事务编排:会话/消息落库、意图路由、脱敏、调 Python | QaService, IntentClassifier, Anonymizer, SmartLayerClient, AccessGuard |
| `IntentClassifier`(接口) | 问题文本 → Intent(枚举) | — |
| `RuleIntentClassifier` | 关键词/正则规则实现(默认) | — |
| `Intent`(枚举) | GENERAL / ASSESSMENT / TEACHING / AIDS / RESEARCH | — |
| `SmartLayerClient`(接口) | 调 Python 智能层(generate answer) | — |
| `HttpSmartLayerClient` | REST 调用(HTTP/1.1),失败优雅降级 | — |
| `QaService` | 领域逻辑:会话/消息 CRUD | QaConversationRepository, QaMessageRepository |
| `QaConversation` / `QaMessage`(实体) | 领域对象(明文) | — |
| `*Repository` + `*Mapper` + XML | 持久化(镜像 report 四件套) | — |

意图分类器、SmartLayerClient 都是**可切换适配器**:`RuleIntentClassifier` 默认,后续可加 `LlmIntentClassifier`;`HttpSmartLayerClient` 调真 Python,测试可注入桩。

### 3.2 Python 侧(ai-smart-layer/app)

| 组件 | 职责 | 默认实现 |
|---|---|---|
| `app/agents/qa.py` | qa Agent 编排:接收脱敏问题 → RAG → 答案 | 真实编排(调下面管线) |
| `app/rag/pipeline.py` | RAG 管线:嵌入 → 检索 → 拼上下文 → LLM 生成 | mock 嵌入 + mock 检索 + mock LLM |
| `app/rag/embedder.py`(新) | 嵌入适配器(接口 + mock) | MockEmbedder(确定性伪向量) |
| `app/rag/retriever.py`(新) | 向量检索适配器(接口 + mock + milvus 占位) | MockRetriever(返回固定桩文档) |
| `app/adapters/llm.py` | LLM 适配器(已有 mock/openai) | MockLLM |
| `app/main.py` | FastAPI:POST /v1/agents/qa/invoke 接入真实 qa 编排 | — |

## 4. 数据模型(Spec §6.8,镜像 report 四件套)

`qa_conversation`:
- `id` BIGINT PK AUTO_INCREMENT
- `user_id` BIGINT(归属用户;行级权限依据)
- `title` VARCHAR(会话标题,首条问题截断生成)
- `created_at` / `updated_at` DATETIME
- `deleted` TINYINT(软删除)

`qa_message`:
- `id` BIGINT PK AUTO_INCREMENT
- `conversation_id` BIGINT(外键 qa_conversation)
- `role` VARCHAR(USER / ASSISTANT)
- `content` TEXT(明文落库。问答文本不是"残障儿童档案 PII 字段"(那些经 FieldCipher 加密在 child 等表),故不加密存储;但若用户在问题里写了儿童姓名等 PII,出网 Python 前经脱敏管线替换为占位符,返回后还原——落库的是还原后的用户原文)
- `route_to` VARCHAR(NULL 或 assessment/teaching/aids/research,业务意图时填)
- `sources` JSON(RAG 引用来源数组,ASSISTANT 消息;无则空数组)
- `created_at` DATETIME
- `deleted` TINYINT

> MyBatis 风格沿用:Mapper 收发 `Map<String,Object>`;XML resultMap snake_case→camelCase;insert `useGeneratedKeys` 写回 id;Repository 手动类型转换。schema 变更先改 `schema.sql`(backend 模块 resources)。

## 5. 接口规范

### 5.1 端点(均经网关 `/api/qa/**` 路由到 agent-qa:8084)

**POST /api/qa/ask** — 提问
```json
请求: { "conversationId": 123, "question": "孤独症儿童的融合教育政策有哪些?" }
       (conversationId 可空 → 新建会话)
响应 data: {
  "conversationId": 123,
  "answer": "……(通用意图时为 RAG 答案;业务意图时为引导语)",
  "routeTo": null,           // 业务意图时:assessment/teaching/aids/research
  "deepLink": null,          // 业务意图时:前端深链路径,如 /assessment?...
  "sources": [ {"title":"…","source":"…"} ],  // RAG 引用,业务意图时空
  "messageId": 456
}
```

**GET /api/qa/conversations** — 我的会话列表(分页)
```json
响应 data: { "list": [ {"id","title","createdAt"} ], "total", "page", "size" }
```

**GET /api/qa/conversations/{id}** — 会话历史(校验归属)
```json
响应 data: { "id", "title", "messages": [ {"id","role","content","routeTo","sources","createdAt"} ] }
```

### 5.2 Java→Python 内部接口

**POST /v1/agents/qa/invoke**(Python FastAPI,内网)
```json
请求: { "question": "[儿童1] 的融合教育政策?", "topK": 5 }   // question 已脱敏
响应: { "answer": "……", "sources": [ {"title","source"} ], "mock": true }
```

### 5.3 通用约定(继承平台)

- 统一 `Result<T>` 信封;错误码分段(4xxx AI/脱敏,5xxx 系统)。
- 网关注入 `X-User-Id` 等头;agent-qa 信任并解析。
- 业务意图深链映射:ASSESSMENT→`/assessment`、TEACHING→`/teaching`、AIDS→`/aids`、RESEARCH→`/research`(前端路由,具体 query 由前端补)。

## 6. 意图路由规则(RuleIntentClassifier 首版)

关键词命中(大小写不敏感,中文包含匹配),优先级自上而下,首个命中即返回:

| Intent | 触发关键词(示例,实现时可扩) | 路由 |
|---|---|---|
| ASSESSMENT | 评估、量表、测评、评分、CARS、ABC 量表 | routeTo=assessment,深链 /assessment |
| TEACHING | 备课、教案、课件、教学设计、分层教学 | routeTo=teaching,深链 /teaching |
| AIDS | 教具、绘本、文生图、AR、VR、资源库 | routeTo=aids,深链 /aids |
| RESEARCH | 文献、信效度、课题、论文、Cronbach | routeTo=research,深链 /research |
| GENERAL(兜底) | 以上都不命中 | 调 Python RAG 回答 |

> 规则是接口实现,后续 `LlmIntentClassifier` 可替换;GENERAL 是默认兜底,确保任何问题都有响应。

## 7. 脱敏红线(继承平台,本 Agent 落地)

1. **出网必经脱敏**:GENERAL 意图调 Python 前,Java 用 `Anonymizer` 对 question 做 PII 占位符替换(姓名/学校→`[儿童N]`/`[学校N]`);Python 只见脱敏文本。脱敏失败**硬阻断**(返回 4xxx,不调 Python)。
2. **返回还原**:Python 返回的 answer/sources 在 Java 侧用脱敏映射还原后再落库/返回。
3. **mock 一致性**:mock LLM 路径也走完整脱敏→还原链,保证 mock 与真实路径行为一致、测试可断言。
4. **Python 侧无明文 PII**:Python 不持久化、不回写业务库,只在内存处理脱敏文本。qa_message.content 在 **Java 侧**还原后落库(见 §4 字段说明:问答文本非加密范畴,PII 已由脱敏管线处理)。
5. **自由文本正则兜底**:`RegexAnonymizer` 对任何自由文本还会自动正则替换身份证号(`[身份证N]`)、中国手机号(`[电话N]`)、电子邮件(`[邮箱N]`),作为无需提供已知名单的最后一道防线。姓名/学校脱敏依赖调用方传入已知名单——qa 业务层未传入儿童姓名列表(问题是用户自由输入),因此**用户应避免在提问中输入真实儿童或家长姓名**;按姓名脱敏的 NER(命名实体识别)能力是后续增强项。

## 8. 错误处理

| 环节 | 策略 |
|---|---|
| 脱敏失败 | 硬阻断:返回错误码(4xxx),不调 Python;USER 消息仍记录 |
| Python 不可用/超时 | 优雅降级:返回"问答服务暂不可用,请稍后重试";USER 消息已记录,不写 ASSISTANT 消息(或写降级占位) |
| 意图分类无命中 | 兜底 GENERAL(总有响应) |
| 会话不存在/越权 | AccessGuard 拒绝:他人会话 403;不存在 404 |
| RAG 检索为空 | Python 返回空 sources + LLM 基于无检索上下文作答(降级提示"未检索到相关资料") |

## 9. 测试策略

### 9.1 Java(@SpringBootTest + MockMvc,@ActiveProfiles("test"))
- **意图路由**:业务意图(评估/备课/教具/科研)→ 返回对应 routeTo+深链,**不调 Python**(注入 SmartLayerClient 桩,断言未被调用);GENERAL → 调 Python 桩返回。
- **会话持久化**:ask 新建会话 + 写 USER/ASSISTANT 消息;会话历史/列表正确返回。
- **行级权限**:他人会话 GET → 403;ask 到他人 conversationId → 403。
- **脱敏**:GENERAL 意图,断言传给 SmartLayerClient 的 question 是脱敏后文本(含占位符,不含原始姓名);返回还原。
- **降级**:SmartLayerClient 桩抛异常 → 返回降级响应,USER 消息仍在。
- token 用 `AuthTestSupport.registerAndLogin`;响应断言 `$.data...`;方法名中文。

### 9.2 Python(pytest,mock 适配器)
- 意图分类不在 Python(在 Java);Python 测 RAG 管线:mock 嵌入→mock 检索→mock LLM,断言返回结构(answer 非空、sources 为列表)。
- 脱敏占位符在 Python 侧**不被还原**(Python 不持明文)——断言收到 `[儿童1]` 原样进入 LLM prompt。
- LLM/嵌入/检索全 mock,不外联;httpx ASGITransport 测 /v1/agents/qa/invoke。

### 9.3 质量门禁
- backend(含 qa 实体/表若放 backend)或 sellm-agent-qa 模块测试绿;全 reactor `clean install` SUCCESS(用 clean,避免 stale target 假绿);Python pytest 绿(若环境无 3.11 则静态审查);前端无关本期。

## 10. 模块归属决策

qa 业务实体/表/Mapper 放在 **sellm-agent-qa 模块**(它已是独立服务,有自己的 com.sellm.qa 包),依赖 sellm-common-core(Result/枚举/AccessGuard/Anonymizer 接口等)。qa_conversation/qa_message 的 schema.sql 放 sellm-agent-qa 的 resources(其有独立数据源配置;dev/test 用 H2)。

> 注:agent-qa 此前仅空壳(spring-boot-starter-web + actuator + sellm-common-core)。本期需给它加:mybatis/jdbc/h2(持久化)、调 Python 的 HTTP 客户端。这些依赖加在 sellm-agent-qa 模块自身 pom(不污染 core)。

## 11. 待实现前明确(不阻塞设计)

- agent-qa 的数据源:dev/test 用 H2(与 backend 一致模式);prod MySQL。需在 sellm-agent-qa 加 application.yml 数据源 + schema 初始化。
- Python 智能层 qa endpoint 的真实 LangGraph 编排(首版可不上 LangGraph 状态图,单步管线即可;YAGNI)。
- kb_policy 知识库灌库(首版 mock 检索返回桩文档,真实灌库后续)。
