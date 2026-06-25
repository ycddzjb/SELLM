# 计划:评估干预(康复)Agent 需求变更 —— 多模态诊断 + IEP 个案干预计划

> 分支 feature/special-edu-llm,worktree `dev_workspace`,后端 `backend/` 单体(根包 com.sellm)。
> 本期范围(已与用户确认):**多模态诊断 + IEP 干预计划**。训练对比评估【下期】。

## 决策(已确认)
- **落地范围**:分阶段。先做①多模态诊断 + ②IEP 干预计划;③训练对比评估下期(它依赖①②的产出闭环)。
- **多模态深度**:文本+图片+**语音(ASR)+视频(抽帧)** 四模态都要"识别"(非仅上传存储)。
- **诊断与 IEP 关系**:**新建独立诊断模块**(结构化诊断结果:维度能力等级/现存障碍/能力缺陷),IEP 生成从"只吃 reportId 文本"改为"吃诊断结果"。
- **知识库**:沿用关键词检索,给 `knowledge_doc` **加分类列**(SCALE_SYSTEM/IEP_CASE/POLICY_ETHICS)并**灌种子语料**;不引入向量库。

## 现状关键事实(调研结论)
- IEP 现状:`iep/` 模块完整,但 IEP 只吃 `reportId` 的纯文本结论;prompt 仅【长期/短期目标+干预活动】三段纯文本;无结构化训练字段;无合规约束。DRAFT→finalize→PDF 闭环健全。
- 多模态现状:`multimodal/MultimodalModel.analyze(byte[], note, items)` 只接**单张图片**(写死 image/jpeg),产 `ItemSuggestion`(itemId/分/理由)。**无 ASR、无视频**。`assessment/media/EvaluationMediaController` 有上传+analyze,note 出网脱敏硬阻断已落地。
- 适配器装配:`MultimodalConfig` 按 `sellm.multimodal.provider`+apiKey 切 mock/openai。新增 ASR/视频照此模式。
- AiGateway:`generate(PromptRequest{prompt,names,schools})` 脱敏→模型→还原,脱敏失败硬阻断。
- 四件套样板:`IepRecord`(实体)+`IepRecordMapper`(收发 Map)+`mybatis/IepRecordMapper.xml`(snake→camel resultMap,insert useGeneratedKeys)+`IepRecordRepository`(手动 Number 转 long)。新表照此镜像。
- RAG:`RagRetriever.retrieve(query, topK)` 关键词命中计数;`knowledge_doc(doc_id,content,source)` 无分类列,种子为空。
- PDF:`export/PdfExporter.toPdf(title, content)` 纯文本入参。
- 权限:端点级 `SecurityConfig` 有序 requestMatchers(POST/PUT /api/assessments|reports|ieps 限 TEACHER/MANAGER);行级 `AccessGuard.checkChildAccess`。
- 红线:脱敏硬阻断 / AI 只产 DRAFT 人工定稿 / PII 经 FieldCipher 加密落库。

<!-- PLACEHOLDER -->

## 架构设计

### 数据模型(新增表,镜像四件套)
- `diagnosis`(诊断记录):id, child_id, owner_id, input_summary(TEXT,结构化训练表现摘要 JSON), dimensions(TEXT,JSON:各维度能力等级/现存障碍/能力缺陷), draft(TEXT,诊断报告草案), finalized_content(TEXT), status(DRAFT/FINALIZED), created_at。
- `diagnosis_media`(诊断关联多模态素材):id, diagnosis_id, media_type(TEXT/IMAGE/VIDEO/AUDIO), object_key, transcript(TEXT,ASR/视频识别结果), note_text, created_at。(也可复用现有 evaluation_media 加 diagnosis_id 外键——见"取舍")
- `iep` 表扩展:加 `diagnosis_id BIGINT`(可空,新链路用);保留 `report_id` 可空(旧链路兼容)。
- `knowledge_doc` 加 `category VARCHAR(32)`(SCALE_SYSTEM/IEP_CASE/POLICY_ETHICS,可空兼容旧数据)。

### 后端模块
**A. 多模态识别扩展(multimodal/)**
- 新增 `SpeechModel`(ASR:audio bytes→文本)接口 + Mock + 真实适配器(默认 mock 不外联,provider+key 才真);`sellm.speech.*` 配置。
- 视频:新增 `VideoAnalyzer` 抽帧(取首帧/关键帧→复用 vision)或视频理解模型接口 + Mock。首版可"抽帧→现有 vision",降低成本。
- 媒体类型分流:analyze 按 media_type 路由(IMAGE→vision, AUDIO→ASR→文本, VIDEO→抽帧→vision, TEXT→直接文本)。
- 红线:所有出网前文本/note 脱敏硬阻断;ASR/视频适配器把"发请求"抽成 protected send(...) 便于测试注入假响应;真实 HTTP 强制 HTTP/1.1。

**B. 诊断模块(新建 diagnosis/)**
- 四件套:`Diagnosis` 实体 + Mapper + XML + Repository(镜像 iep)。
- `DiagnosisService`(领域):聚合多模态识别结果(各模态→文本/分)+ 结构化训练表现输入 + 量表知识库 RAG 召回 → 拼 prompt → AiGateway.generate → 产出结构化诊断(维度能力等级/障碍/缺陷)+ 叙述报告草案。prompt 要求模型输出可解析结构(维度 JSON + 报告正文)。
- `DiagnosisAppService`:行级 AccessGuard;落 DRAFT;edit/finalize/get/listByChild。
- `DiagnosisController` `/api/diagnoses`:POST 生成、GET/{id}、GET ?childId=、PUT/{id}(编辑)、POST/{id}/finalize、GET/{id}/pdf(仅 FINALIZED)。
- 多模态素材:POST `/api/diagnoses/{id}/media`(上传+识别,复用 ObjectStorage + 分流识别),或先建诊断再挂素材。

**C. IEP 改吃诊断结果**
- `GenerateIepRequest` 加 `diagnosisId`(与 reportId 二选一,优先 diagnosisId)。
- `IepAppService.generate` 分支:有 diagnosisId → 取 Diagnosis 的结构化维度+报告作为输入;否则走旧 reportId 链路(兼容)。
- `IepService.generateDraft` prompt 升级:
  - 输入加"诊断维度结果(能力等级/障碍/缺陷)"。
  - 训练维度结构化:【动作】【语言训练】【社交互动】【认知培养】【生活自理】各含"训练方式/频次/步骤(如剥珠训练)"。
  - **合规约束**:prompt 注入政策法规/伦理标准约束(从 knowledge_doc category=POLICY_ETHICS 召回),要求规避不合理干预(如厌恶疗法/体罚等),生成后做关键词 red-flag 校验,命中则标注警示。
  - RAG 召回按 category 分维度:IEP_CASE(个案范例)+ POLICY_ETHICS(合规)。

**D. 知识库分类 + 灌语料**
- `knowledge_doc` 加 category 列;`KnowledgeDocMapper`/Repository 支持按 category 检索(`retrieveByCategory(query, category, topK)`,RagRetriever 接口扩展或新增方法)。
- seed-dev.sql 灌种子:量表体系/IEP个案/政策法规各若干条(政策法规含《特殊教育提升计划》、伦理底线等)。

### 前端(frontend/)
- 新增 `views/DiagnosisView.vue`:多模态输入(文本/图片/视频/语音上传 + 结构化训练表现表单如剥珠正确率/眼神互动)→ 生成诊断 → 维度结果展示(能力等级/障碍/缺陷)→ 编辑 → 定稿 → PDF。
- `IepView.vue` 改:生成入口从"选报告"增加"选诊断";展示结构化训练维度。
- `api/diagnosis.js` 新增;`api/ieps.js` 加 diagnosisId 入参。
- 导航/Dashboard:评估干预工作台加"多模态诊断"入口。

## 取舍点(实现中遇到再定,默认取前者)
- 诊断素材表:**新建 diagnosis_media**(语义清晰) vs 复用 evaluation_media 加 diagnosis_id(改动小)。默认新建。
- 视频:**抽帧复用 vision**(首版,成本低) vs 真实视频理解模型。默认抽帧。
- 结构化维度存储:JSON 字段(灵活,首版) vs 独立维度行表(可查询/对比,为下期对比铺路)。默认 JSON,下期对比时再范式化。

## 实施步骤(分多次提交,每步带测试)
1. **schema + 知识库分类**:加 diagnosis/diagnosis_media 表、iep.diagnosis_id、knowledge_doc.category;seed 灌语料。RagRetriever 加按 category 检索。
2. **多模态识别扩展**:SpeechModel(ASR)+ 视频抽帧 + media_type 分流 + Mock + 配置。测试:各模态 mock 识别 + 脱敏硬阻断。
3. **诊断模块**:四件套 + DiagnosisService(聚合+RAG+AiGateway+结构化产出)+ AppService + Controller + PDF。测试:生成 DRAFT/编辑/定稿冻结/行级权限/脱敏硬阻断。
4. **IEP 改吃诊断 + 结构化训练 + 合规**:GenerateIepRequest 加 diagnosisId;prompt 升级五维度+方式/频次/步骤;合规约束+red-flag 校验。测试:基于诊断生成、合规 red-flag、兼容旧 reportId 链路。
5. **前端**:DiagnosisView + IepView 改造 + api + 导航。前端 build。
6. **联调+文档**:端到端 curl;更新 CLAUDE_CHANGES.md + INTEGRATION.md。

## 验证
- 后端:每步 `./backend/mvnw test`(H2 test profile);全量 clean package。
- 前端:`npm run build`。
- 端到端:文本+图片+(mock)语音/视频输入→诊断→IEP,经测试覆盖脱敏硬阻断、DRAFT 冻结、行级权限、合规 red-flag。

## 不做(本期外)
- ③训练对比评估(训练结果上传、阶段评估、纵向对比、方案适配性判断、保留有效/优化低效)——下期。需训练记录/阶段/版本表,依赖本期诊断+IEP 闭环。
- 向量检索(Milvus):本期关键词+分类,不引入。
- 真实视频理解模型(本期抽帧);真实 ASR 默认 mock(配 key 才外联)。

## 风险
- 工作量大(4 模态识别 + 诊断模块 + IEP 重构 + 合规 + 前端),分 6 步提交,每步独立可测。
- ASR/视频真实外联默认关闭(mock),避免联调卡网络;真实适配器抽 send() 便于测试。
- 结构化诊断输出依赖模型按格式产 JSON,需 prompt 约束 + 容错解析(解析失败降级纯文本)。
- 合规 red-flag 是关键词兜底,非语义,可能漏判;prompt 约束为主、校验为辅,文案说明"AI 辅助、人工把关"。

