# 智能教具 Agent(agent-aids)设计文档

> 文档日期:2026-06-20
> 状态:设计已确认,待用户复核
> 隶属:特殊教育垂直大模型平台 · 第 4 个 Agent(智能教具)· 第四个真实业务 Agent(五大 Agent 收官)
> 平台 Spec:`docs/spec/special_edu_llm_spec.md`(§1.2 边界 / §5.3 长任务异步范式 / §6.7 aids 表)
> 复用:`sellm-agent-common`(异常/属性/AbstractHttpSmartLayerClient/AgentExceptionHandler/自动装配)+ ObjectStorage 自动装配(core)

## 1. 定位与范围

Spec 五大 Agent 的第 4 个,平台**第四个真实业务 Agent**(qa/teaching/research 之后),五大 Agent 业务收官。首版两功能:

- **教具推荐**:按障碍类型推荐特殊教具。**纯 Java 查库,不调 LLM、不出网、不脱敏。**
- **文生素材**:数字化教具生成(文生图/绘本/视频/音频),**长任务异步范式(Spec §5.3:202 + 轮询)**,mock LLM 生成,产物落对象存储。

### 1.1 第一版范围

- **在范围**:教具推荐(teaching_aid 查库)+ 文生素材(generated_asset,@Async 异步 + 轮询);各持久化 + 鉴权;文生素材走脱敏 + Python mock LLM + ObjectStorage 产物。
- **不在第一版(YAGNI)**:资源库(resource_library 表,需 Milvus + 向量化)推后;AR/VR 交互(需渲染引擎)推后;真实文生图/视频模型默认不启用(mock);真实媒体二进制产物(首版产物为 mock LLM 文本描述/占位,经 ObjectStorage 存储,真实媒体生成是后续可切换适配器)。

### 1.2 与现有 Agent 的关系

- 复用 agent-common 全套脚手架(零复制)+ ObjectStorage 自动装配(teaching 已引入 core)。
- **文生素材是平台首个长任务异步范式落地**(Spec §5.3):202 + taskId,后台 @Async,轮询查状态。这是该 Agent 最独特的能力,为后续真实文生视频(必然耗时)铺范式。

## 2. 架构与调用链

```
用户(老师)→ 网关(JWT 验签 + 注入 X-User-* + 限流)
  → Java sellm-agent-aids(依赖 sellm-agent-common):

    教具推荐(纯本地查库,无 Python/无脱敏):
      GET /api/aids/recommendations?disorderType=ASD  → 查 teaching_aid → 返回列表

    文生素材(长任务异步,Spec §5.3):
      POST /api/aids/assets {type, prompt}
        → 立即 202 Accepted, data:{ taskId };落 generated_asset(status=PENDING)
        → @Async 后台线程:
            status=RUNNING → Anonymizer 脱敏 prompt → 调 Python(mock LLM 生成素材描述)
            → 还原 → ObjectStorage.put 产物 → status=SUCCESS(回填 storage_key)
            (脱敏失败/Python 失败 → status=FAILED + error,绝不出网原文)
      GET /api/aids/tasks/{taskId}  → 轮询 { status, result?, error? }
      GET /api/aids/assets/{id}     → 查看产物(行级权限)
      GET /api/aids/assets          → 我的素材列表

  Python 侧(ai-smart-layer,仅文生素材):
    POST /v1/agents/aids/invoke {type, prompt(脱敏)} → mock LLM 生成素材文本描述 → {content}
```

### 2.1 职责边界

- **教具推荐**:RecommendAppService 本地查 teaching_aid,完全不涉网络/脱敏。
- **文生素材**:AssetAppService 编排异步任务;@Async 后台执行脱敏→Python→存储→状态机。
- 鉴权 X-User-Id;行级权限(asset 归属 owner)。Python 不持业务库、不还原。

## 3. 组件设计

### 3.1 Java 侧(sellm-agent-aids,根包 com.sellm.aids)

| 组件 | 职责 |
|---|---|
| `RecommendController` | 教具推荐 REST 端点;X-User-Id 鉴权 |
| `RecommendAppService` | 按 disorderType 查 teaching_aid(纯本地) |
| `TeachingAid`(实体)+ Mapper + XML + Repository | 教具库持久化(seed 数据) |
| `AssetController` | 文生素材端点(POST 202 / GET tasks / GET assets) |
| `AssetAppService` | 落 PENDING + 触发 @Async 任务 + 查状态 |
| `AssetGenerationTask` | `@Async` 后台:脱敏→Python→存储→状态机(SUCCESS/FAILED) |
| `GeneratedAsset`(实体)+ Mapper + XML + Repository | 素材产物持久化 |
| `AssetType`(枚举 IMAGE/PICTUREBOOK/VIDEO/AUDIO)/ `AssetStatus`(PENDING/RUNNING/SUCCESS/FAILED) | 值对象 |
| `SmartLayerClient`(typed)+ `HttpAidsSmartLayerClient` | 调 Python `/v1/agents/aids/invoke`(继承 AbstractHttpSmartLayerClient) |
| `AsyncConfig` | `@EnableAsync` + TaskExecutor(生产异步;测试可覆盖为同步) |
| (异常/异常处理器/属性)| **不写,来自 sellm-agent-common** |

> 鉴权守卫:Controller 用 `@RequestHeader("X-User-Id") Long userId`,缺则抛 `com.sellm.agentcommon.UnauthorizedException`(→401);行级权限手动比对 ownerId。

### 3.2 Python 侧(ai-smart-layer/app,仅文生素材)

| 组件 | 职责 |
|---|---|
| `app/agents/aids.py` | aids 编排:接收脱敏 prompt + type → mock LLM 生成素材文本描述 |
| `app/main.py` | 新增 `POST /v1/agents/aids/invoke` |

教具推荐**无 Python 参与**。

### 3.3 长任务异步设计(Spec §5.3,核心)

**流程**:
1. `POST /api/aids/assets {type, prompt}`:AssetAppService 落 generated_asset(owner_id, type, prompt, status=PENDING, task_id=UUID),返回 `202 Accepted, data:{ taskId }`(taskId 用 generated_asset.id 或独立 UUID;本设计用 **id 作 taskId**,简化:GET /tasks/{id} 即查该 asset 状态)。
2. AssetAppService 触发 `assetGenerationTask.run(assetId)`(`@Async`)。
3. `AssetGenerationTask.run`(后台线程):
   - status → RUNNING(可选,首版可直接 PENDING→终态)
   - Anonymizer 脱敏 prompt;脱敏失败 → status=FAILED, error="脱敏阻断",**return(绝不出网)**
   - 调 smartLayer.generate(type, anonymizedPrompt);失败(SmartLayerException)→ status=FAILED, error
   - 成功 → 还原文本 → ObjectStorage.put(产物字节)→ status=SUCCESS, storage_key 回填
   - 全程 catch 兜底:任何异常 → status=FAILED + error(任务不崩溃后台线程)
4. `GET /api/aids/tasks/{taskId}`:查 generated_asset,返回 `{ status, result?(SUCCESS 时含 storage_key/type), error?(FAILED 时) }`(行级权限:仅本人 asset)。

**异步可测性(关键)**:生产用真实 `@Async`(TaskExecutor);**测试用同步 executor**(`SyncTaskExecutor` 或测试 @Primary TaskExecutor bean),使 POST 返回后后台任务已同步执行完、达终态,可直接断言 SUCCESS/FAILED。范式真实(生产异步)+ 可测(测试同步)。

## 4. 数据模型(Spec §6.7 的 2/3 表,镜像四件套)

`teaching_aid`(推荐库,seed):
- `id` BIGINT PK AUTO_INCREMENT
- `name` VARCHAR(教具名)
- `disorder_types` TEXT(JSON 数组,适用障碍类型如 ["ASD","ADHD"])
- `category` VARCHAR(类别)
- `usage_guide` TEXT(使用指导)
- `created_at` / `updated_at` / `deleted`

`generated_asset`(文生素材产物):
- `id` BIGINT PK AUTO_INCREMENT(亦作 taskId)
- `owner_id` BIGINT(行级权限)
- `type` VARCHAR(IMAGE/PICTUREBOOK/VIDEO/AUDIO)
- `prompt` TEXT(生成提示;明文落库,出网前脱敏)
- `storage_key` VARCHAR(SUCCESS 后产物对象 key)
- `task_id` VARCHAR(任务标识;本设计 = id 字符串,保留字段对齐 Spec)
- `status` VARCHAR(PENDING/RUNNING/SUCCESS/FAILED)
- `error` TEXT(FAILED 时原因)
- `created_at` / `updated_at` / `deleted`

> 四件套 MyBatis 风格沿用;schema.sql 放 sellm-agent-aids/resources;teaching_aid seed 数据用 seed.sql 或 schema.sql 末尾 INSERT(dev/test)。teaching_aid 推荐查询:`disorder_types LIKE %"ASD"%`(JSON 字符串包含匹配,首版简化;真实可后续 JSON 函数)。

## 5. 接口规范

### 5.1 教具推荐端点

**GET /api/aids/recommendations?disorderType=ASD** — 按障碍类型推荐
```json
响应 data: [
  { "id":1, "name":"视觉时间表卡片", "category":"视觉支持",
    "disorderTypes":["ASD","ADHD"], "usageGuide":"用于..." }, ...
]
```
（disorderType 可空 → 返回全部教具;无匹配 → 空列表）

### 5.2 文生素材端点(长任务异步)

**POST /api/aids/assets** — 提交生成任务
```json
请求: { "type": "PICTUREBOOK", "prompt": "为自闭症儿童设计认识情绪的绘本" }
响应: HTTP 202, data: { "taskId": 5 }
```
**GET /api/aids/tasks/{taskId}** — 轮询任务状态
```json
响应 data: {
  "status": "SUCCESS",                       // PENDING|RUNNING|SUCCESS|FAILED
  "result": { "type":"PICTUREBOOK", "storageKey":"asset/5.txt" },  // SUCCESS 时
  "error": null                              // FAILED 时含原因
}
```
**GET /api/aids/assets/{id}** — 查看素材(行级权限);**GET /api/aids/assets** — 我的素材列表

### 5.3 Java→Python 内部接口(仅文生素材)

**POST /v1/agents/aids/invoke**
```json
请求: { "type": "PICTUREBOOK", "prompt": "(脱敏后)..." }
响应: { "content": "(素材文本描述/占位)", "mock": true }
```

### 5.4 通用约定

- 统一 `Result<T>` 信封;**POST /assets 返 202**(异步范式),其余 200。
- ErrorCode 用现有(INVALID_INPUT/ANONYMIZATION_FAILED/ACCESS_DENIED/NOT_FOUND/UNAUTHORIZED)。
- 配置 `sellm.smart-layer.*`(agent-common)+ `sellm.storage.local-dir`(ObjectStorage)。
- AgentExceptionHandler 来自 agent-common(自动装配)。

## 6. 红线落地

| 红线 | 教具推荐 | 文生素材 |
|---|---|---|
| 出网必经脱敏 | **不适用**(纯本地查库) | prompt 出网前 Anonymizer 脱敏(@Async 任务内);脱敏失败 → 任务 FAILED,**绝不调 Python**;返回还原 |
| AI 只产草案 | **不适用**(查库非生成) | 素材是"生成产物";SUCCESS 后老师确认取用(不自动用于正式教学);status 区分中间态/终态 |
| Python 不持明文 | **不适用** | Python 只见脱敏 prompt,不持久化、不还原 |

> 文生素材的 prompt 可能含儿童信息(如"为张三设计..."),故出网前脱敏。脱敏在 @Async 后台任务内执行,失败则任务 FAILED 而非抛给用户(异步语义);用户轮询见 FAILED + error。

## 7. 错误处理

| 环节 | 策略 |
|---|---|
| 教具推荐 disorderType 空 | 返回全部教具(非错误) |
| 文生素材 prompt 空 | POST 即 INVALID_INPUT(400),不建任务 |
| 文生素材脱敏失败 | @Async 任务内:status=FAILED, error="脱敏校验未通过,已阻断出网";不调 Python;POST 已返 202(任务异步失败,轮询可见) |
| 文生素材 Python 不可用 | @Async 任务内:status=FAILED, error="智能层调用失败";产物不生成 |
| @Async 任务任意异常 | 全包 catch → status=FAILED + error;后台线程不崩 |
| 轮询不存在的 taskId | 404(NOT_FOUND) |
| 他人 asset / task | 403(ACCESS_DENIED)|
| 缺 X-User-Id | 401 |

> 异步语义:POST 阶段只校验"能否受理"(prompt 非空)→ 202;实际生成失败体现在任务状态(轮询 FAILED),不在 POST 响应。脱敏硬阻断在任务内 = 任务 FAILED(仍守"不出网原文"红线)。

## 8. 测试策略

### 8.1 教具推荐(@SpringBootTest + MockMvc,H2 + seed)
- GET ?disorderType=ASD → 返回含 ASD 的教具;disorderType 空 → 全部;无匹配类型 → 空列表。
- 缺 X-User-Id → 401。

### 8.2 文生素材异步(关键:测试用同步 executor)
- **测试配置覆盖 TaskExecutor 为 SyncTaskExecutor**(@TestConfiguration @Primary),使 @Async 同步执行,POST 返回后任务已达终态。
- POST → 202 + taskId;落 generated_asset;同步执行后查 GET /tasks/{taskId} → SUCCESS + storageKey(SmartLayerClient 桩 + ObjectStorage Noop)。
- 脱敏硬阻断:@Primary 抛 AnonymizationException 的 Anonymizer + SmartLayerClient 桩 → 任务 FAILED + error;断言 stub.called == false(不调 Python)。
- Python 不可用:SmartLayerClient 桩抛 SmartLayerException → 任务 FAILED + error。
- 行级权限:他人 asset GET → 403;他人 task 轮询 → 403;不存在 taskId → 404;缺头 → 401。
- prompt 空 → POST 400。
- 脱敏验证:断言传 Python 的 prompt 是脱敏后(含占位符)。

### 8.3 Python(pytest,mock)
- aids 编排:type+prompt → mock LLM → 非空 content;占位符不还原。

### 8.4 质量门禁
- 全 reactor `clean install`(clean)SUCCESS;agent-aids 测试绿;backend 242 / qa 16 / teaching 15 / research 20 不受影响;Python pytest 绿或静态审查。

## 9. 模块归属与依赖

- 全在 **sellm-agent-aids** 模块(com.sellm.aids),依赖 **sellm-agent-common**(脚手架)+ 经它传递 core(Anonymizer/ObjectStorage/Result 自动装配)。
- aids pom 加:mybatis/jdbc/h2。ObjectStorage 经 core 自动装配(NoopObjectStorage 默认)。
- `@EnableAsync` + AsyncConfig(TaskExecutor)。反应堆模块数不变(agent-aids 已存在为空壳,本期填充)。

## 10. 实现前明确(不阻塞)

- agent-aids 数据源:dev/test H2;prod MySQL。
- teaching_aid seed:dev/test 用 schema.sql 末尾 INSERT 几条种子(如视觉时间表卡片/沙盘/感统训练器材),覆盖 ASD/ADHD/感统等。
- 文生素材产物:首版 mock LLM 文本描述存为 .txt(ObjectStorage),真实图/视频是后续可切换适配器。
- @Async 任务的事务:任务内多次更新 generated_asset status,无需跨服务事务;每次更新独立提交(后台线程,非请求事务)。**异步可见性陷阱(实现者必读)**:POST 落 PENDING 的插入必须在触发 @Async 前**已提交**,否则后台线程可能读不到该行。两种安全做法:(a)AssetAppService.submit 不加 @Transactional,save(PENDING) 后立即 update 提交,再调 @Async 任务(传 assetId);(b)更稳妥——@Async 任务方法不依赖"另一线程刚插入的行可见性",而是 submit 方法 save 后**把 assetId 传给 @Async 任务**,任务内 findById 重查;由于 save 已独立提交(Repository 非事务包裹或 submit 无 @Transactional),重查可见。首版用 (a):submit 不加 @Transactional,save→返回 202→触发 @Async(assetId),任务内 findById 能查到已提交的 PENDING 行。测试用同步 executor 时天然无可见性问题(同线程)。
- taskId = generated_asset.id(简化);task_id 字段保留对齐 Spec(存 id 字符串或 UUID,本设计存 id 字符串)。
- Python aids 编排首版无 LangGraph(单步 prompt + mock LLM)。
