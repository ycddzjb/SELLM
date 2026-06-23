# 阶段 E — 真实大模型接入(评估报告 / IEP / 家庭 IEP)Implementation Plan

## Context

这是项目核心诉求:把特殊教育评估/IEP 从 Mock 升级为真实大模型驱动。**关键利好**:现有架构已为此设计好——`DefaultAiGateway` 已封装「脱敏 → 调模型 → 还原」三步,业务层(ReportService/IepService)只依赖 `AiGateway`;唯一需要替换的是 `AiModel.complete(String)` 这一个接口。本阶段不动网关与业务 prompt 组装,只:(1) 加一个可切换的真实模型适配器(OpenAI 兼容,默认仍 Mock);(2) 新增家长家庭 IEP 端到端;(3) 报告/IEP 定稿后 PDF 下载。

## 设计决策(已与用户确认)

1. **可切换适配器,默认 Mock**:新增 `OpenAiCompatibleModel`(实现 `AiModel`),通过配置 `sellm.ai.provider` 切换(`mock` 默认 / `openai`)。未配置 api-key 时保持 Mock,**不出网、测试不挂**。这是密钥/出网红线的关键——默认不外联,需显式配置才启用。
2. **OpenAI 兼容接口**:`POST {base-url}/v1/chat/completions`,`base-url`/`model`/`api-key` 全可配。兼容 DeepSeek、通义、Moonshot、vLLM 等。脱敏在网关层已做——传给模型的是 `[儿童1]`/`[学校1]` 占位符,真实姓名/校名不出网。
3. **本期含家庭 IEP**:家长设定 IEP 目标 → 系统取该儿童最新定稿报告 → 大模型按目标生成家庭训练 IEP 草案。家长端点 + 行级(仅自己孩子)。
4. **PDF 下载**:报告/IEP **定稿后**可下载 PDF。引入轻量 PDF 库(openhtmltopdf + 中文字体),后端返回 PDF 字节流。

## 现状关键点(已探查)

- `AiModel`(`backend/.../aigateway/AiModel.java`):`String complete(String anonymizedPrompt)` —— 唯一替换点。`MockAiModel` 现为唯一 `@Component` 实现。
- `DefaultAiGateway`:脱敏→`aiModel.complete`→还原;模型抛异常包装为 `AiGatewayException`。**不改**。
- `ReportService`/`IepService`:RAG 召回 + 组 prompt + `aiGateway.generate(new PromptRequest(prompt, names, schools))`。**prompt 已带脱敏名单**。本期可微调 prompt(指令更明确),但结构不变。
- `ReportAppService.generate`/`IepAppService.generate`:行级权限 + 落库 DRAFT;`finalize` 定稿。
- 配置:`application.yml` 用 `${ENV:default}` 注入密钥(crypto/jwt 已是此模式);新增 `sellm.ai.*` 同样模式。
- pom:目前无 HTTP client(用 JDK `java.net.http.HttpClient` 即可,无需加依赖)与 PDF 库(需加 openhtmltopdf)。
- 测试:`DefaultAiGatewayTest` 已示范用 lambda `AiModel` 桩验证脱敏——真实适配器测试同法(不真连网)。

---

## 文件结构(本计划范围)

```
backend/
  pom.xml                              # 加 openhtmltopdf-pdfbox(PDF)
  src/main/resources/
    application.yml                    # sellm.ai.provider/base-url/api-key/model
    application-dev.yml                # 显式 provider: mock(dev 不出网)
  src/main/java/com/sellm/aigateway/
    AiProperties.java                  # @ConfigurationProperties sellm.ai
    OpenAiCompatibleModel.java         # 新:实现 AiModel,JDK HttpClient 调 /v1/chat/completions
    AiModelConfig.java                 # @Bean 按 provider 选 Mock/OpenAi(@Primary 切换)
    (MockAiModel 保留,降级为非 @Primary 或由 config 决定)
  src/main/java/com/sellm/iep/
    FamilyIepService.java              # 家庭 IEP 领域服务(RAG+AI,prompt 含家长目标)
    FamilyIepAppService.java           # 取最新定稿报告 + 行级 + 落库
    FamilyIepController.java           # POST /api/family-ieps(家长)
    dto/GenerateFamilyIepRequest.java  # childId + parentGoal
    (复用 IepRecord 落库,或新 family_iep 表——见 Task 4)
  src/main/java/com/sellm/export/
    PdfExporter.java                   # 文本→PDF 字节流(openhtmltopdf)
    报告/IEP 下载端点加在各自 Controller
backend/src/test/java/com/sellm/
    aigateway/OpenAiCompatibleModelTest.java   # 请求体/响应解析(桩 HttpClient 或可注入)
    aigateway/AiModelConfigTest.java           # provider=mock 时注入 Mock
    iep/FamilyIepApiTest.java
    export/PdfExporterTest.java
frontend/src/
    api/ieps.js / reports.js           # 下载(blob)+ 家庭IEP
    views/ReportView.vue / IepView.vue # 定稿后"下载PDF"按钮
    views/FamilyIepView.vue            # 家长端:设目标→生成→查看(家长工作台)
    router + MainLayout                # 家长菜单"家庭IEP"
```

---

### Task 1: AiModel 可切换适配器(OpenAI 兼容,默认 Mock)(TDD)

**核心红线任务**:默认 Mock 不出网;真实适配器仅在配置后启用,脱敏由网关保证。

**Files:** AiProperties, OpenAiCompatibleModel, AiModelConfig, MockAiModel(调整), application.yml/dev, OpenAiCompatibleModelTest, AiModelConfigTest

- [x] **Step 1:** `AiProperties`(`@ConfigurationProperties("sellm.ai")`):provider(默认 "mock")、baseUrl、apiKey、model、timeoutSeconds(默认 30)。
- [x] **Step 2:** `OpenAiCompatibleModel implements AiModel`:JDK `HttpClient` POST `{baseUrl}/v1/chat/completions`,body `{model, messages:[{role:user, content:prompt}]}`,header `Authorization: Bearer {apiKey}`;解析 `choices[0].message.content`;非 2xx / 解析失败抛 RuntimeException(网关会包成 AiGatewayException)。用 Jackson 组/解 JSON。**不在构造期连网**。
- [x] **Step 3:** `AiModelConfig`:`@Bean @Primary AiModel aiModel(AiProperties)` —— provider=="openai" 且 apiKey 非空 → OpenAiCompatibleModel,否则 MockAiModel。MockAiModel 去掉 `@Component`(由 config 产出),保留类。
- [x] **Step 4:** application.yml 加 `sellm.ai.provider: ${SELLM_AI_PROVIDER:mock}` 等;application-dev.yml 显式 `provider: mock`。
- [x] **Step 5:** 测试:
  - `OpenAiCompatibleModelTest`:构造请求体含 model/messages、Authorization;响应 JSON 解析出 content(把 HttpClient 抽成可注入或用本地 stub server/可覆写的 send 方法——优先把"发请求"抽成可覆写 protected 方法,测试子类化注入假响应,不真连网)。
  - `AiModelConfigTest`:provider=mock → 实例是 MockAiModel;provider=openai+key → 是 OpenAiCompatibleModel。
- [x] **Step 6:** 全量回归绿(默认 mock,既有 AiGateway 测试不变)→ Commit

---

### Task 2: 报告/IEP prompt 增强 + 消费儿童档案上下文(TDD)

让真实模型产出更可用:把阶段 D 的儿童档案上下文(基线/月度目标等)纳入 prompt;指令更结构化(分节、给可编辑草案)。**仍走 AiGateway,脱敏名单不变**。

**Files:** ReportService, IepService(微调 prompt),对应 service 测试

- [x] **Step 1:** ReportService.generateDraft 入参补充可选的儿童档案上下文(baselineSummary/monthlyGoal 等),拼进 prompt"既往档案"段;指令明确"输出分节评估报告草案,供教师编辑定稿"。ReportAppService 传入 child 的扩展字段。
- [x] **Step 2:** IepService 同理:prompt 纳入档案上下文 + 明确"长短期目标 + 干预活动"结构。
- [x] **Step 3:** 测试:用桩 AiModel 捕获 prompt,断言含档案上下文且**不含**明文姓名(脱敏);既有 ReportServiceTest/IepServiceTest 适配。
- [x] **Step 4:** 全量回归绿 → Commit

---

### Task 3: PDF 下载(报告 + IEP)(TDD)

定稿后可下载 PDF。

**Files:** pom.xml, export/PdfExporter, ReportController/IepController 加下载端点, SecurityConfig, PdfExporterTest

- [x] **Step 1:** pom 加 `com.openhtmltopdf:openhtmltopdf-pdfbox`(锁定版本)+ 内置/打包一个中文字体(或用 PDFBox 标准字体 + 注意中文——openhtmltopdf 需注册中文 TTF;选一个开源思源/文泉驿子集放 resources)。
- [x] **Step 2:** `PdfExporter.toPdf(String title, String content) -> byte[]`:把定稿文本包成简单 HTML(标题+正文,保留换行),渲染 PDF。
- [x] **Step 3:** `GET /api/reports/{id}/pdf`、`GET /api/ieps/{id}/pdf`:行级权限(经 AppService.get 已校验);仅 status==FINALIZED 才允许(否则 400 "请先定稿");返回 `application/pdf` + `Content-Disposition: attachment`。
- [x] **Step 4:** SecurityConfig:`GET /api/reports/*/pdf`、`/api/ieps/*/pdf` authenticated(行级在 service)。
- [x] **Step 5:** 测试:`PdfExporterTest`(产出非空、以 `%PDF` 开头);API 测试(未定稿下载 400;定稿后 200 + content-type)。
- [x] **Step 6:** 全量回归绿 → Commit

---

### Task 4: 家长家庭 IEP(后端,TDD)

家长设目标 → 取该儿童最新定稿报告 → 大模型出家庭训练 IEP。

**Files:** schema(family_iep 表)、FamilyIepService/AppService/Controller/DTO、SecurityConfig、FamilyIepApiTest

- [x] **Step 1:** schema 加 `family_iep`(id/child_id/parent_user_id/parent_goal/draft/finalized_content/status/created_at),复用 record 范式;Mapper/XML/Repository。
- [x] **Step 2:** `FamilyIepService.generateDraft(childName, schoolName, parentGoal, latestReportConclusion)`:RAG 召回家庭训练策略 + prompt(含家长目标 + 最新评估结论)→ AiGateway。
- [x] **Step 3:** `FamilyIepAppService.generate(childId, parentGoal)`:行级 `checkChildAccess`(家长仅自己孩子)；取该 child 最新 **FINALIZED** 报告作结论(无定稿报告 → 400 "请等待评估报告定稿")；落库 family_iep DRAFT。get/listByChild/finalize 同 IEP 范式。
- [x] **Step 4:** `FamilyIepController` `/api/family-ieps`:POST 生成(childId+parentGoal)、GET 列表(?childId)、GET/{id}、PUT/{id}/finalize。SecurityConfig:authenticated(行级在 service，家长能访问自己孩子)。
- [x] **Step 5:** FamilyIepApiTest:家长对自己孩子生成(有定稿报告)→ 成功;无定稿报告 → 400;对别人孩子 → 403;列表/定稿。
- [x] **Step 6:** 全量回归绿 → Commit

---

### Task 5: 前端 — PDF 下载按钮 + 家长家庭 IEP 页

**Files:** api/reports.js/ieps.js(+ familyIeps.js)、ReportView/IepView、FamilyIepView、router、MainLayout

- [x] **Step 1:** reports.js/ieps.js 加 `downloadPdf(id)`(http get responseType blob → 触发浏览器下载);ReportView/IepView 定稿后显示"下载 PDF"按钮。
- [x] **Step 2:** familyIeps.js（generate/list/get/finalize）；FamilyIepView.vue:家长选自己孩子 + 填 IEP 目标 → 生成 → 展示草案 → 可编辑定稿 → 下载。
- [x] **Step 3:** MainLayout 家长菜单加"家庭 IEP";router 加 `/family-iep`;家长登录后可见(auth.isParent)。
- [x] **Step 4:** `npm run build` 通过 → Commit

---

### Task 6: 端到端联调

dev(provider=mock,不出网)起后端 + curl:走 评估→报告(Mock草案)→定稿→下载PDF;IEP 同;家长家庭 IEP(需先有定稿报告);并验证 `sellm.ai.provider` 切到 openai+假 base-url 时 mock 仍可回退/或显式 mock 路径不外联。记录 INTEGRATION.md。

- [x] **Step 1:** 起 dev 后端(默认 mock)
- [x] **Step 2:** curl:老师 评估→生成报告→定稿→GET /reports/{id}/pdf(200, %PDF);生成 IEP→定稿→PDF;家长 family-iep(自己孩子+有定稿报告)→成功,无定稿→400,别人孩子→403。
- [x] **Step 3:** 停服务,追加 INTEGRATION.md,提交

---

## 验证清单

1. **后端全量回归** `./mvnw test` 全绿(现 189 + 新增约 15-20)
2. **前端 build** `npm run build` 无错误
3. **端到端 curl** 全通(默认 mock 不出网)
4. **红线(最重要)**:
   - **默认 provider=mock,不外联**;真实模型需显式配置 api-key 才启用
   - 出网内容经网关脱敏(`[儿童N]`/`[学校N]`),真实姓名/校名不出网——由现有 DefaultAiGateway 保证,适配器只收脱敏文本
   - api-key 走环境变量 `${SELLM_AI_API_KEY:}`,不硬编码、不入库、不打日志
   - AI 仅产草案,人工定稿;下载是定稿后
   - 行级权限延续(家庭 IEP 家长限自己孩子)
5. **兼容**:AiGateway/Anonymizer 不改;既有 Mock 测试全绿;新依赖锁版本

## 后续(不在本计划)

- 多模态(课堂视频/照片 → 智能识别量表指标)——需多模态模型 + MinIO 文件存储,独立大阶段
- 真实模型联调(需用户提供 base-url/api-key 在自己环境验证;本计划交付可切换骨架 + Mock 全绿)
