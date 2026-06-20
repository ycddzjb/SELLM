# 阶段 G — 自动匹配量表 + 图像脱敏管线 Implementation Plan

## Context

规格 7.1 多模态「后续」剩余两项,本期一并落地:
1. **自动匹配量表**:评估时按儿童的障碍类型推荐适配量表,老师从推荐列表选(仍可手选全部),省去盲选。
2. **图像本地脱敏管线**:真实多模态出网会把含儿童面部的图片发给第三方。本期在 vision 出网前插一道**可切换的图像脱敏层**(抽接口 + 默认 Noop;真实打码接外部 CV 服务),把"图像 PII 出网"从"仅靠 Mock 不出网"升级为"有一个可启用的脱敏拦截点"。

## 设计决策(已与用户确认)

1. **图像脱敏 = 抽接口 + 默认 Noop**:新增 `ImageAnonymizer` 接口(`sanitize(byte[]) -> byte[]`),默认 `NoopImageAnonymizer`(原样返回,不改图)+ `HttpImageAnonymizer`(POST 图片到外部 CV 打码服务,provider 配置后启用)。**不引 OpenCV**,与项目"不引重原生依赖"风格一致。脱敏层插在 `OpenAiVisionModel` 出网前——保证真实 vision 启用时图像先过脱敏。
2. **两块一起一个 PR**(阶段 G)。
3. **匹配量表 = 按障碍类型推荐**:新增 `GET /api/children/{childId}/recommended-scales`(行级)——读 `child.disorderType` → `ScaleRepository.listByDisorderType` → 返回推荐量表;前端评估页选定儿童后默认拉推荐,老师可切"全部量表"手选。复用现成仓储,零新表。

## 现状关键点(已探查)

- `ScaleRepository.listByDisorderType(disorderType)` **已存在**;`GET /api/scales?disorderType=` 已支持。推荐只差一个绑定 child 的行级端点。
- `Child.disorderType` 单值(对应 DisorderType 枚举名)。
- 图像出网点精确在 `EvaluationMediaController` L93-94:`bytes = objectStorage.get(...)` → `multimodalModel.analyze(bytes, ...)`。脱敏要插在 `OpenAiVisionModel` 内部出网前(而非 controller),这样 Mock 路径不受影响、真实 vision 必过脱敏。
- 适配器/配置/行级/record 模式齐全可镜像(同阶段 E/F)。

## 红线

- 图像脱敏**默认 Noop**(不改图);真实打码服务需显式配 `sellm.image-anon.provider=http` + endpoint。
- 即便 Noop,真实 vision 仍默认关闭(multimodal=mock)——双层默认不外联。
- 脱敏服务 endpoint/凭据走环境变量。
- 推荐量表经 `AccessGuard` 行级(老师限本机构、家长限自己孩子)。
- 推荐不强制:老师仍可手选任意量表(不锁死)。

---

## 文件结构(本计划范围)

```
backend/src/main/java/com/sellm/
  multimodal/
    ImageAnonymizer.java            # 接口:sanitize(byte[]) -> byte[]
    NoopImageAnonymizer.java        # 默认:原样返回
    HttpImageAnonymizer.java        # 外部 CV 打码服务(provider=http 时);send() protected
    ImageAnonProperties.java        # @ConfigurationProperties sellm.image-anon
    ImageAnonConfig.java            # 按 provider 装配
    OpenAiVisionModel.java          # 改:出网前过 ImageAnonymizer.sanitize
  scale/
    ScaleController.java            # (不动)
  assessment/media/
    (analyze 不变 —— 脱敏在 vision 内部)
  child/
    ChildController.java 或新 RecommendController  # GET /api/children/{id}/recommended-scales
backend/src/main/resources/
  application.yml / application-dev.yml          # sellm.image-anon.*
backend/src/test/java/com/sellm/
  multimodal/ImageAnonConfigTest.java
  multimodal/HttpImageAnonymizerTest.java
  multimodal/OpenAiVisionModelTest.java          # 补:出网前调用脱敏
  child/RecommendedScalesApiTest.java
frontend/src/
  api/scales.js 或 children.js                    # recommendedScales(childId)
  views/AssessmentView.vue                        # 选儿童→默认拉推荐量表 + "全部量表"切换
.env.example                                      # SELLM_IMAGE_ANON_*
```

---

### Task 1: 推荐量表(按障碍类型,行级)(TDD)

**Files:** ChildController(加端点), RecommendedScalesApiTest

- [ ] **Step 1:** `GET /api/children/{childId}/recommended-scales`:`requireAccess(childId)`(行级)→ 读 child.disorderType → 若非空 `scaleRepository.listByDisorderType(type)`,空则返回空列表(老师手选全部)→ 复用 `ScaleResponse`(头信息)。注入 ScaleRepository。
- [ ] **Step 2:** SecurityConfig:`GET /api/children/*/recommended-scales` 命中现有 `/api/**` authenticated 即可(行级在 controller),无需新规则(GET 不被 child 写规则影响)。
- [ ] **Step 3:** RecommendedScalesApiTest:老师对本机构 ASD 儿童 → 推荐含 ASD 量表、不含他类;child.disorderType 为空 → 空列表;他机构老师 → 403;家长自己孩子 → 200。
- [ ] **Step 4:** 全量回归绿 → Commit。

---

### Task 2: 图像脱敏管线(抽接口 + 默认 Noop)(TDD)

**Files:** multimodal/ImageAnonymizer.java + Noop/Http/Properties/Config, application.yml/dev, 测试

- [ ] **Step 1:** `ImageAnonymizer` 接口:`byte[] sanitize(byte[] image)`(null/空原样返回)。
- [ ] **Step 2:** `NoopImageAnonymizer`(默认:原样返回)。
- [ ] **Step 3:** `ImageAnonProperties`(`sellm.image-anon`):provider(默认 "noop" | "http")、endpoint、apiKey、timeoutSeconds。
- [ ] **Step 4:** `HttpImageAnonymizer`:POST 图片字节到外部打码服务(`{endpoint}` 返回打码后图片字节);`send()` protected 便于测试不连网;失败 fail-safe——**抛异常阻断**(脱敏失败绝不让原图出网,符合现有 Anonymizer "脱敏失败硬阻断"红线)。
- [ ] **Step 5:** `ImageAnonConfig` 按 provider 装配(默认 Noop)。
- [ ] **Step 6:** application.yml `sellm.image-anon.*` env 模式;dev 默认 noop。
- [ ] **Step 7:** 测试:ImageAnonConfigTest(provider 选择);HttpImageAnonymizerTest(send 抽 protected 注入假打码响应、失败抛异常)。全量回归绿 → Commit。

---

### Task 3: vision 出网前接入脱敏(TDD)

**Files:** OpenAiVisionModel.java, MultimodalConfig.java, OpenAiVisionModelTest

- [ ] **Step 1:** `OpenAiVisionModel` 构造注入 `ImageAnonymizer`;`analyze` 里 `media = imageAnonymizer.sanitize(media)` **在 buildRequestBody 之前**(出网图片先脱敏)。
- [ ] **Step 2:** `MultimodalConfig.multimodalModel` 注入 ImageAnonymizer 传给 OpenAiVisionModel(Mock 路径不需要,Mock 不出网)。
- [ ] **Step 3:** OpenAiVisionModelTest 补:注入一个"标记式"ImageAnonymizer(返回固定字节),断言 buildRequestBody 用的是脱敏后字节(出网前确实过了脱敏)。
- [ ] **Step 4:** 全量回归绿 → Commit。

> 说明:默认 multimodal=mock 时整条 vision 不触发,脱敏层不参与;仅当显式启用真实 vision 时,图像必经脱敏层(默认 Noop 不改图,配 http 才真打码)。这是"可启用的拦截点",非强制改图。

---

### Task 4: 前端 — 评估页默认拉推荐量表

**Files:** api(recommendedScales), AssessmentView.vue

- [ ] **Step 1:** api 加 `recommendedScales(childId)`。
- [ ] **Step 2:** AssessmentView:有 childId 时,量表下拉默认用"推荐量表"(按该儿童障碍类型),加一个"显示全部量表"开关切回 listScales 全量;推荐为空时自动回退全部 + 提示。
- [ ] **Step 3:** `npm run build` 通过 → Commit。

---

### Task 5: 配置文档 + 端到端联调 + PR

- [ ] **Step 1:** .env.example 加 `SELLM_IMAGE_ANON_*`(无真值 + 风险说明:默认 noop 不改图,启用 http 接外部打码服务)。
- [ ] **Step 2:** dev 起后端,curl:老师对 ASD 儿童取 recommended-scales(含 ASD 量表);他机构 403;disorderType 空 → 空列表。(脱敏默认 noop + multimodal mock,vision 不触发,故图像脱敏走单测覆盖。)
- [ ] **Step 3:** INTEGRATION.md 记录;停服务;提交;开 PR。

---

## 验证清单

1. 后端全量回归 `./mvnw test` 全绿(现 225 + 新增约 10-12)
2. 前端 `npm run build` 通过
3. 端到端 curl(推荐量表行级)全通
4. **红线**:图像脱敏默认 noop、真实打码需显式配且失败硬阻断;multimodal 仍默认 mock(双层不外联);推荐量表行级 + 不强制;凭据环境变量
5. 兼容:现有评估/多模态/计分不破;Mock 路径不触发脱敏层

## 后续(不在本计划)

- 视频抽帧/多帧;OpenCV 本地人脸检测(若未来接受原生依赖)
- 推荐量表的更细策略(年龄段/历史评估加权)
