# 科研助手 Agent(agent-research)设计文档

> 文档日期:2026-06-20
> 状态:设计已确认,待用户复核
> 隶属:特殊教育垂直大模型平台 · 第 3 个 Agent(科研助手)· 第三个真实业务 Agent
> 平台 Spec:`docs/spec/special_edu_llm_spec.md`(§1.2 边界 / §6.6 research 表)
> 复用:`sellm-agent-common`(异常/属性/AbstractHttpSmartLayerClient/AgentExceptionHandler/自动装配)+ teaching 草案定稿模板

## 1. 定位与范围

Spec 五大 Agent 的第 3 个,平台**第三个真实业务 Agent**(qa、teaching 之后)。首版两功能:

- **信效度计算**:量表的内部一致性等信度统计(Cronbach α + 分半信度 + 项总相关)。**纯 Java 本地数值计算,不调 LLM、不出网、不脱敏。**
- **课题申报书撰写**:mock LLM 生成课题书草案,人工编辑定稿(复用 teaching/qa 的草案定稿 + agent-common 模板)。

### 1.1 第一版范围

- **在范围**:信效度计算(reliability_calc)+ 课题申报书(research_proposal);各持久化 + 鉴权;课题书走 Python mock LLM + 脱敏 + 草案定稿。
- **不在第一版(YAGNI)**:文献检索(literature 表,需 Milvus + 真实检索)、个人知识库(personal_kb_doc 表,需向量存储)——推后;因子分析等更复杂统计量推后;真实 LLM 默认不启用(mock)。

### 1.2 与现有 Agent 的关系

- **课题书部分**复用 agent-common 全套脚手架(零复制)+ teaching 的草案定稿状态机模式(DRAFT→编辑→FINALIZED,含已定稿冻结)。
- **信效度部分是模板的有价值变体**:纯本地计算,**无 Python 协作、无网络面、无脱敏**——展示 Agent 也可承载不出网的确定性算法能力。

## 2. 架构与调用链

```
用户(科研者)→ 网关(JWT 验签 + 注入 X-User-* + 限流)
  → Java sellm-agent-research(依赖 sellm-agent-common):

    信效度(纯本地,无 Python):
      POST /api/research/reliability        传分数矩阵 → ReliabilityService 本地算 → 存 → 返回
      GET  /api/research/reliability/{id}    查看(行级权限)
      GET  /api/research/reliability          我的计算列表

    课题书(mock LLM,草案定稿,复用模板):
      POST /api/research/proposals           生成草案(DRAFT):脱敏 topic → Python → 还原
      PUT  /api/research/proposals/{id}       编辑(未定稿)
      POST /api/research/proposals/{id}/finalize  定稿(FINALIZED,冻结)
      GET  /api/research/proposals/{id}       查看
      GET  /api/research/proposals            我的课题书列表

  Python 侧(ai-smart-layer,仅课题书):
    POST /v1/agents/research/invoke {topic(脱敏)} → mock LLM 生成课题书文本 → {content}
```

### 2.1 职责边界

- **信效度**:`ReliabilityAppService` + `ReliabilityService`(纯算法)完全本地完成,不调 SmartLayerClient、不经 Anonymizer。输入是匿名「被试×题目」分数矩阵。
- **课题书**:`ProposalAppService` 编排脱敏→Python→还原→状态机,同 teaching。Python 不持业务库、不还原。
- 鉴权:X-User-Id 头(agent-research 无 spring-security);行级权限:资源 ownerId == userId。

## 3. 组件设计

### 3.1 Java 侧(sellm-agent-research,根包 com.sellm.research)

| 组件 | 职责 |
|---|---|
| `ReliabilityController` | 信效度 REST 端点;X-User-Id 鉴权 |
| `ReliabilityAppService` | 行级权限 + 事务:校验输入、调 ReliabilityService、存 reliability_calc |
| `ReliabilityService` | **纯算法**:Cronbach α / 分半信度 / 项总相关(无 Spring 依赖外的副作用,可纯单测) |
| `ReliabilityCalc`(实体)+ Mapper + XML + Repository | 持久化(四件套) |
| `ProposalController` | 课题书 REST 端点 |
| `ProposalAppService` | 草案定稿编排:脱敏→Python→还原→状态机→冻结(镜像 teaching) |
| `ResearchProposal`(实体)+ Mapper + XML + Repository | 持久化(四件套) |
| `SmartLayerClient`(接口,typed)+ `HttpResearchSmartLayerClient` | 调 Python `/v1/agents/research/invoke`(继承 AbstractHttpSmartLayerClient) |
| (异常/异常处理器/属性)| **不写,来自 sellm-agent-common** |

> 鉴权守卫:Controller 用 `@RequestHeader("X-User-Id") Long userId`,缺则抛 `com.sellm.agentcommon.UnauthorizedException`(→401 经 AgentExceptionHandler);行级权限手动比对 ownerId(同 teaching/qa)。

### 3.2 Python 侧(ai-smart-layer/app,仅课题书)

| 组件 | 职责 |
|---|---|
| `app/agents/research.py` | research 编排:接收脱敏 topic → mock LLM 生成课题申报书文本 |
| `app/main.py` | 新增 `POST /v1/agents/research/invoke` |

信效度**无 Python 参与**。

### 3.3 ReliabilityService 算法(纯 Java,核心)

输入:`double[][] scores`(行=被试 i,列=题目 j;矩阵 N 被试 × K 题)。输出三统计量:

- **Cronbach α** = (K/(K-1)) × (1 − Σ(题方差) / 总分方差)。其中题方差 = 每列(题)的方差,总分方差 = 每被试总分的方差。**题方差与总分方差必须用同一方差约定**(本设计统一用总体方差,除以 N;因 α 是两方差之比,只要一致,/N 与 /(N-1) 结果相同——实现者务必一致,勿一处 /N 一处 /(N-1))。
- **分半信度(split-half, Spearman-Brown 校正)**:按题目奇偶分两半,各被试两半得分求 Pearson 相关 r,校正信度 = 2r/(1+r)。
- **项总相关(item-total correlation)**:每题 j 与「总分」的 Pearson 相关(数组,每题一个值)。

边界处理(明确,不抛 NaN/异常给上层):
- K<2(题数不足)→ α 无法算:返回 result 标注 `"alpha": null, "note": "题数不足"`(分半同理需 K≥2)。
- N<2(被试不足)→ 方差/相关无法算:标注 null + note。
- 零方差(总分方差=0 或某题方差=0)→ 相关分母 0:该统计量返回 null + note,不传播 NaN。

result JSON 形如:
```json
{ "alpha": 0.82, "splitHalf": 0.79, "itemTotal": [0.65, 0.71, ...],
  "itemCount": 5, "subjectCount": 30, "notes": [] }
```

## 4. 数据模型(Spec §6.6 的 2/4 表,镜像四件套)

`reliability_calc`:
- `id` BIGINT PK AUTO_INCREMENT
- `owner_id` BIGINT(行级权限)
- `dataset` TEXT(输入分数矩阵 JSON,留痕)
- `method` VARCHAR(首版固定 `"cronbach+splithalf+itemtotal"`,预留多方法)
- `result` TEXT(结果 JSON)
- `created_at` / `updated_at` / `deleted`

`research_proposal`:
- `id` BIGINT PK AUTO_INCREMENT
- `owner_id` BIGINT
- `topic` VARCHAR(课题主题,生成输入)
- `ai_draft` TEXT(AI 原稿留痕)
- `content` TEXT(可编辑稿/定稿)
- `status` VARCHAR(DRAFT/FINALIZED)
- `created_at` / `updated_at` / `deleted`

> 四件套 MyBatis 风格沿用:Mapper 收发 `Map<String,Object>`;XML resultMap snake→camel;insert useGeneratedKeys;Repository 手动类型转换。schema.sql 放 sellm-agent-research/resources;dataset/result 存 JSON 字符串(由 AppService 用 ObjectMapper 序列化)。

## 5. 接口规范

### 5.1 信效度端点

**POST /api/research/reliability** — 计算信效度
```json
请求: {
  "method": "cronbach+splithalf+itemtotal",   // 可空,默认全算
  "scores": [[4,3,5,2,4],[3,3,4,2,3], ...]     // 被试×题目矩阵(匿名分数,无 PII)
}
响应 data: {
  "id": 1,
  "result": { "alpha": 0.82, "splitHalf": 0.79, "itemTotal": [...],
              "itemCount": 5, "subjectCount": 30, "notes": [] }
}
```
**GET /api/research/reliability/{id}** / **GET /api/research/reliability** — 查看/我的列表(行级权限)

### 5.2 课题书端点(同 teaching 草案定稿形态)

- `POST /api/research/proposals`(body { topic })→ 生成草案 DRAFT(脱敏 topic → Python → 还原)
- `PUT /api/research/proposals/{id}`(body { content })→ 编辑(已定稿→400)
- `POST /api/research/proposals/{id}/finalize` → FINALIZED
- `GET /api/research/proposals/{id}` / `GET /api/research/proposals` → 查看/列表

### 5.3 Java→Python 内部接口(仅课题书)

**POST /v1/agents/research/invoke**
```json
请求: { "topic": "(脱敏后)特殊教育融合班级师资配置研究" }
响应: { "content": "(课题申报书文本)", "mock": true }
```

### 5.4 通用约定

- 统一 `Result<T>` 信封;ErrorCode 用现有(INVALID_INPUT/ANONYMIZATION_FAILED/ACCESS_DENIED/NOT_FOUND/UNAUTHORIZED)。
- 配置 `sellm.smart-layer.*`(agent-common 提供,research 共用)。
- AgentExceptionHandler 来自 agent-common(自动装配),research 不写自己的。

## 6. 红线落地

| 红线 | 信效度 | 课题书 |
|---|---|---|
| 出网必经脱敏 | **不适用**(纯本地,无网络面;输入约定为匿名分数矩阵) | topic 出网前 Anonymizer 脱敏,失败硬阻断,返回还原 |
| AI 只产草案 | **不适用**(确定性算法,非 AI 生成,无草案概念) | 课题书 DRAFT→编辑→FINALIZED,已定稿冻结 |
| Python 不持明文 | **不适用**(不调 Python) | Python 只见脱敏 topic,不持久化、不还原 |

> 信效度输入是匿名「被试×题目」分数矩阵(数值),调用方不传姓名等 PII;这是接口契约。若未来需要关联具体儿童,再引入脱敏。

## 7. 错误处理

| 环节 | 策略 |
|---|---|
| 信效度输入非法 | scores 为空/非矩形(行长不一)/全空 → INVALID_INPUT(400),不存记录 |
| 信效度统计量不可算 | K<2 / N<2 / 零方差 → 该统计量 result 置 null + notes 说明(**正常返回 200**,不报错;计算成功只是部分量无意义) |
| 课题书脱敏失败 | 硬阻断 ANONYMIZATION_FAILED(400),不调 Python;DRAFT 已落(content 空) |
| 课题书 Python 不可用 | 优雅降级 content="AI 生成失败,可重试或手动撰写";DRAFT 保留 |
| 已定稿编辑 | 400(已定稿不可编辑) |
| 他人资源 / 不存在 | 403(ACCESS_DENIED)/ 404(NOT_FOUND) |
| 缺 X-User-Id | 401(UnauthorizedException→AgentExceptionHandler) |

> 信效度的「不可算」与「非法输入」区分:非法输入(空/非矩形)是 400 错误;可算但某统计量无意义(题数不足等)是 200 + null + note。

## 8. 测试策略

### 8.1 ReliabilityService 单测(纯算法,核心,无 Spring)
- 已知矩阵 → 已知 α(用教科书/手算标准值断言,容差 1e-4):如一个 K=3、N≥5 的小矩阵,α 对标已知值。
- 分半信度:偶数题矩阵,两半相关 + Spearman-Brown 校正对标已知值。
- 项总相关:每题与总分 Pearson,数组长度=题数,值对标。
- 边界:K=1→alpha null+note;N=1→null+note;某题零方差→itemTotal 该项 null+note;全同分→总分方差 0→相关 null。
- 非矩形矩阵 → AppService 层 INVALID_INPUT(Service 可抛 IllegalArgumentException,AppService 转 BusinessException)。

### 8.2 课题书 API 测试(@SpringBootTest + MockMvc,H2,镜像 teaching)
- 生成草案→DRAFT(SmartLayerClient 桩);编辑;finalize→FINALIZED;已定稿编辑→400。
- 脱敏:断言传 Python 的 topic 是脱敏后;返回还原。脱敏硬阻断独立测试类(@Primary 抛 AnonymizationException 桩→400 且不调 Python)。
- 降级:桩抛 SmartLayerException→DRAFT 保留 + 降级 content。
- 行级权限:他人课题书 403;缺头 401。

### 8.3 信效度 API 测试
- POST 合法矩阵→200 + result 含 alpha/splitHalf/itemTotal + 落库;GET 查看;他人记录 403;非法矩阵→400。

### 8.4 Python(pytest,mock)
- research 编排:topic→mock LLM→非空 content;占位符不还原(Python 不持明文)。

### 8.5 质量门禁
- 全 reactor `clean install`(clean)SUCCESS;agent-research 测试绿;backend 242 / qa 16 / teaching 15 不受影响;Python pytest 绿或静态审查。

## 9. 模块归属与依赖

- 全在 **sellm-agent-research** 模块(com.sellm.research),依赖 **sellm-agent-common**(脚手架)+ 经它传递 sellm-common-core。
- agent-research pom 需加:mybatis/jdbc/h2(持久化)+ sellm-agent-common。jackson 来自 web。Anonymizer 经 core 自动装配;SmartLayerProperties/异常处理器经 agent-common 自动装配。
- 反应堆模块数不变(agent-research 已存在为空壳,本期填充)。

## 10. 实现前明确(不阻塞)

- agent-research 数据源:dev/test H2;prod MySQL。
- Python research 编排首版无 LangGraph(单步 prompt + mock LLM)。
- ReliabilityService 的 Pearson/方差用基础 Java 实现(不引入统计库,避免新依赖;算法简单)。
- method 字段首版固定值,预留未来多方法分派。
