# 阶段 F — 多模态评估(视频/照片/笔记 → 智能指标建议)Implementation Plan

## Context

规格 7.1「后续」项:在现有「手动选量表按指标评分」之外,增加多模态——上传课堂训练视频/干预照片/训练笔记,大模型读图/读文给出**各量表指标的评分建议**,老师确认后填入正式评估。这是评估的智能化增强,**不替代**手动评分(AI 仅给建议,老师定夺,延续"AI 产草案、人工定稿"红线)。

本期做**最小闭环**:文件上传 → MinIO 存储 → 多模态模型(可切换,默认 Mock)→ 结构化指标建议 → 老师确认后走现有评估计分链路。视频抽帧/批量/自动匹配量表留后续。

## 设计决策(已与用户确认)

1. **范围**:最小闭环(上传→识别→建议→老师确认填入评估)。视频本期按"上传+存储+可选抽单帧给模型",不做复杂转码/多帧。
2. **模型接入**:可切换适配器,默认 Mock——新增 `MultimodalModel` 接口 + `MockMultimodalModel`(默认,不外联)+ `OpenAiVisionModel`(OpenAI 兼容 vision,图片走 base64/url)。镜像现有 `AiModel`/`AiModelConfig`/`OpenAiCompatibleModel` 模式(`send()` 抽 protected 便于测试不连网)。
3. **图像出网红线**:**默认不出网**(provider=mock)。图像含儿童面部属敏感 PII,无法像文本那样占位符脱敏——故真实 vision 调用需用户**显式配置 + 知情同意**,且在配置项与文档显著告知风险。本期交付可切换骨架,默认 Mock 全绿、零出网。

## 现状关键点(已探查)

- **无任何对象存储/文件上传基础**:pom 无 minio,代码无 `MultipartFile`。MinIO + 上传是全新链路。
- **AiModel 适配器模式**可镜像:`AiModelConfig` 按 `provider` 选 Mock/真实;`OpenAiCompatibleModel.send()` 为 protected 便于测试。
- **多模态产出天然映射现有计分**:`ScaleItem` = itemId/dimension/maxScore;模型输出 `[{itemId, score, reason}]` → 老师确认后即现有 `List<Answer>(itemId, score)` 路径,**不改计分引擎**。
- **行级权限**:`AccessGuard.checkChildAccess` —— 上传/识别都必须绑定 child 并校验。
- **配置约定**:`sellm.*` + `${ENV:default}`;dev profile 给安全默认。
- **record 四件套**(Mapper 用 Map、generated key 回填)+ schema `IF NOT EXISTS` + 测试(MockMvc + AuthTestSupport + jdbc seed)模式齐全可镜像。

## 红线(最重要)

- **默认不外联**:`sellm.multimodal.provider=mock`,真实 vision 需显式配 api-key。
- **图像 PII**:真实 vision 出网会把儿童照片发给第三方,无法脱敏——配置项注释 + .env.example + 部署文档显著标注"启用即代表已获监护人知情同意并自担合规风险"。
- **MinIO 凭据**走环境变量,不入库不硬编码。
- **AI 仅产建议**:多模态输出是"指标评分建议",老师必须确认才落正式评估;不自动生成最终评估分。
- **行级权限**:上传/识别/查询媒体均经 `AccessGuard`(老师限本机构、家长限自己孩子)。

---

## 文件结构(本计划范围)

```
backend/
  pom.xml                                   # 加 minio SDK
  src/main/resources/
    application.yml                          # sellm.minio.* + sellm.multimodal.*
    application-dev.yml                      # dev 安全默认(multimodal mock)
    schema.sql                               # evaluation_media 表
    mybatis/EvaluationMediaMapper.xml
  src/main/java/com/sellm/
    storage/
      StorageProperties.java                 # @ConfigurationProperties sellm.minio
      ObjectStorage.java                     # 接口:put/get/presignedUrl
      MinioObjectStorage.java                # MinIO 实现(provider=minio 时启用)
      NoopObjectStorage.java                 # 默认/dev:本地临时目录或内存,不连 MinIO
      StorageConfig.java                     # 按 provider 选实现
    multimodal/
      MultimodalModel.java                   # 接口:analyze(mediaRef, scaleItems) -> List<ItemSuggestion>
      MockMultimodalModel.java               # 默认:回固定/启发式建议,不外联
      OpenAiVisionModel.java                 # OpenAI 兼容 vision(图片 base64),send() protected
      MultimodalProperties.java              # @ConfigurationProperties sellm.multimodal
      MultimodalConfig.java                  # 按 provider 选 Mock/Vision
      ItemSuggestion.java                    # itemId/suggestedScore/reason
    assessment/media/                        # 评估媒体子模块
      EvaluationMedia.java                   # 实体:id/childId/scaleId/type(IMAGE/VIDEO/NOTE)/objectKey/uploaderId/status
      EvaluationMediaMapper.java / Repository
      EvaluationMediaController.java         # 上传 + 触发识别 + 查建议
      dto/{UploadResponse,SuggestionResponse}.java
  src/test/java/com/sellm/
    multimodal/{MockMultimodalModelTest,OpenAiVisionModelTest,MultimodalConfigTest}.java
    storage/StorageConfigTest.java
    assessment/media/EvaluationMediaApiTest.java
frontend/src/
  api/media.js                               # 上传(multipart)+ 取建议
  views/AssessmentView.vue                   # 加"上传素材→AI 建议→一键填入评分"区
.env.example                                 # 加 minio + multimodal 变量(无真值)
```

---

### Task 1: 对象存储抽象 + MinIO(可切换,默认 Noop)(TDD)

**Files:** pom.xml, storage/*.java, application.yml/dev, StorageConfigTest

- [ ] **Step 1:** pom 加 `io.minio:minio`(锁版本)。
- [ ] **Step 2:** `StorageProperties`(`sellm.minio`):provider(默认 "noop" | "minio")、endpoint、accessKey、secretKey、bucket。
- [ ] **Step 3:** `ObjectStorage` 接口:`String put(String key, byte[] data, String contentType)`、`byte[] get(String key)`、`String presignedUrl(String key)`。
- [ ] **Step 4:** `MinioObjectStorage`(用 MinioClient,**懒连接**——不在构造期连),`NoopObjectStorage`(写入本地临时目录 `data/media/`,返回 file:// 或占位,供 dev/test 不依赖 MinIO)。
- [ ] **Step 5:** `StorageConfig`:provider=="minio" 且配置齐全 → MinioObjectStorage,否则 NoopObjectStorage。
- [ ] **Step 6:** application.yml `sellm.minio.*` env 模式;dev 默认 noop。
- [ ] **Step 7:** StorageConfigTest(provider 选择正确;Noop put/get 往返)。全量回归绿 → Commit。

---

### Task 2: 多模态模型适配器(可切换,默认 Mock)(TDD)

**Files:** multimodal/*.java, application.yml/dev, 三个测试

- [ ] **Step 1:** `ItemSuggestion`(itemId / suggestedScore / reason)。
- [ ] **Step 2:** `MultimodalModel` 接口:`List<ItemSuggestion> analyze(byte[] mediaOrNull, String noteText, List<ScaleItem> items)`(图片字节可空——纯笔记走文本)。
- [ ] **Step 3:** `MockMultimodalModel`(默认):对每个 item 给一个确定性建议分(如 maxScore/2)+ reason="[Mock建议]",**不外联**。
- [ ] **Step 4:** `MultimodalProperties`(`sellm.multimodal`):provider(默认 mock|openai)、baseUrl、apiKey、model(如 qwen-vl-plus / gpt-4o)、timeoutSeconds。
- [ ] **Step 5:** `OpenAiVisionModel`:OpenAI 兼容 `/v1/chat/completions`,messages content 含 `{type:image_url, image_url:{url: data:image/...;base64,...}}` + 文本指令(要求按 items 返回 JSON 数组 itemId/score/reason);`send()` protected;解析模型 JSON → List<ItemSuggestion>。强制 HTTP/1.1(同 OpenAiCompatibleModel 经验)。
- [ ] **Step 6:** `MultimodalConfig` 按 provider 装配。
- [ ] **Step 7:** 测试:MockMultimodalModelTest(每 item 有建议、分在 [0,maxScore]);OpenAiVisionModelTest(buildRequestBody 含 image_url + items 指令、parseSuggestions 解析 JSON、子类覆写 send 注入假响应不连网);MultimodalConfigTest(mock/openai 选择)。全量回归绿 → Commit。

---

### Task 3: 评估媒体上传 + 识别端点(TDD)

**Files:** schema.sql, assessment/media/*.java, mybatis xml, SecurityConfig, EvaluationMediaApiTest

- [ ] **Step 1:** schema `evaluation_media`(id / child_id / scale_id / media_type VARCHAR(16) IMAGE·VIDEO·NOTE / object_key / uploader_user_id / status / created_at)。
- [ ] **Step 2:** EvaluationMedia 实体 + Mapper(Map 风格)+ XML + Repository(镜像 ReportRecord 四件套)。
- [ ] **Step 3:** `EvaluationMediaController` `@RequestMapping("/api/children/{childId}/evaluation-media")`:
  - `POST`(multipart:file 可空 + noteText + scaleId + mediaType):行级 `checkChildAccess`;file 存 ObjectStorage 得 objectKey;落库 media 记录(status=UPLOADED);返回 mediaId。
  - `POST /{mediaId}/analyze`:行级;取媒体(图片 bytes / 笔记文本)+ scale items → `MultimodalModel.analyze` → 返回 `List<ItemSuggestion>`(不落正式评估,仅建议);status=ANALYZED。
  - `GET`(?type 可选):列本 child 媒体。
- [ ] **Step 4:** SecurityConfig:`POST/GET /api/children/*/evaluation-media/**` authenticated(行级在 controller);放 `/api/children/**` 写规则之前(允许家长上传自己孩子,同 child_log 经验)。
- [ ] **Step 5:** EvaluationMediaApiTest:老师上传本机构儿童笔记→analyze 得每 item 建议(Mock);他机构老师上传→403;家长上传自己孩子→成功、别人孩子→403;analyze 输出 itemId 与 scale items 对应。全量回归绿 → Commit。

> 说明:老师拿到建议后,用**现有** `POST /api/assessments`(itemId+score)提交正式评估——本期不新增"建议直接落评估"的自动路径(保人工确认红线),前端做"一键把建议填入评分表单"。

---

### Task 4: 前端 — 上传素材 + AI 建议 + 一键填入

**Files:** api/media.js, AssessmentView.vue

- [ ] **Step 1:** media.js:uploadMedia(childId, formData)、analyzeMedia(childId, mediaId)。
- [ ] **Step 2:** AssessmentView 选定量表后,加"AI 辅助评分(可选)"区:上传图片/视频或填训练笔记 → 调 analyze → 展示每指标建议分+理由 → "采纳建议"按钮把 suggestedScore 填进对应 el-rate(老师可改)→ 仍走原提交。
- [ ] **Step 3:** 显著提示:"AI 建议仅供参考,需教师确认;上传含儿童影像请确保已获监护人同意"。
- [ ] **Step 4:** `npm run build` 通过 → Commit。

---

### Task 5: 配置文档 + 端到端联调

- [ ] **Step 1:** .env.example 加 `SELLM_MINIO_*`、`SELLM_MULTIMODAL_*`(无真值)+ 风险注释。
- [ ] **Step 2:** dev(默认 mock + noop 存储)起后端,curl:老师上传笔记 → analyze 得 Mock 建议(每 item 有分);上传图片(小测试图)→ 存储往返;他机构/家长越权 403;笔记→建议→用建议分提交评估走通。
- [ ] **Step 3:** INTEGRATION.md 记录;停服务;提交。
- [ ] **Step 4:** 开 PR。

---

## 验证清单

1. 后端全量回归 `./mvnw test` 全绿(现 205 + 新增约 15-20),**默认 mock+noop 不外联、不依赖 MinIO**
2. 前端 `npm run build` 通过
3. 端到端 curl(默认 Mock)全通
4. **红线**:默认不外联(multimodal mock + storage noop);真实 vision/minio 需显式配置;图像 PII 风险显著告知;AI 仅建议、老师确认;行级权限;凭据走环境变量
5. 兼容:现有评估/计分/AiGateway 不改;新依赖锁版本

## 后续(不在本计划)

- 视频抽帧/转码、多帧时序分析、批量素材
- 自动匹配量表(免老师选)、建议直接预填多份评估
- 图像本地脱敏(人脸打码)后再出网的可选管线
