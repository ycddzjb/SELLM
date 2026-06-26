# 计划:康复 Agent 第二期 —— 训练对比评估

> 分支 feature/special-edu-llm,worktree dev_workspace,后端 backend 单体(根包 com.sellm)。
> 依赖第一期(诊断+IEP)闭环已落地。

## 决策(已确认)
- **阶段组织**:新建 `training_cycle`(训练周期)表主键。一个儿童多个周期,每周期关联 diagnosisId + iepId + 多条训练数据 + 一份阶段评估。
- **提升粒度**:量化 delta + AI 叙述。训练数据上传后教师填/采纳「指标得分」(复用量表 item 或自定义训练项),阶段评估算前后 delta、达标/未达标 + AI 综合叙述。
- **方案适配性**:AI 产建议 + 人工定稿。阶段评估后 AI 对比各阶段给「适配性建议」(有效保留/低效优化),生成新一版 IEP 草稿,教师定稿。AI 只产建议。
- **多模态**:复用第一期 MediaRecognizer(文本/图片/视频/语音识别)。

## 现状可复用
- MediaRecognizer 多模态分流识别(TEXT/IMAGE/VIDEO/AUDIO)。
- diagnosis 模块四件套范式 + DiagnosisService(RAG+AiGateway+结构化产出)。
- IEP 双链路 generate(reportId, diagnosisId);可加 cycleId 链路或复用 diagnosisId。
- 量表 ScaleItem(itemId/dimension/maxScore)作训练指标锚点。
- ObjectStorage、Anonymizer 脱敏硬阻断、AccessGuard 行级权限、PdfExporter、RagRetriever 分类检索。
- 四件套 Map 风格、DRAFT→finalize 红线。

<!-- PLACEHOLDER -->

## 数据模型(新增表,镜像四件套)
- `training_cycle`(训练周期/阶段):id, child_id, owner_id, diagnosis_id(可空), iep_id(可空),
  seq(第几阶段,从1递增), title, status(ACTIVE/CLOSED), created_at。
  一个 child 多个周期,周期串起「诊断→IEP→训练数据→阶段评估」。
- `training_record`(训练数据):id, cycle_id, media_type(TEXT/IMAGE/VIDEO/AUDIO),
  object_key, transcript(识别结果), note_text, scores(JSON:[{itemId/训练项,score,maxScore}]),
  created_at。多模态训练数据 + 教师录入/采纳的指标得分。
- `stage_eval`(阶段评估报告):id, cycle_id, child_id, scores_summary(JSON:各指标本期得分),
  delta_summary(JSON:对比上一周期的 delta/达标判定), draft(AI 叙述:提升/未达标/适配性建议),
  finalized_content, status(DRAFT/FINALIZED), created_at。
- `iep` 已有 diagnosis_id;新增链路按 cycle 重生成 IEP 时复用 diagnosisId 或加 cycle_id(取舍见下)。

## 后端模块(新建 training/)
**A. 周期管理**
- TrainingCycle 四件套 + TrainingCycleAppService + Controller `/api/training-cycles`:
  POST 建周期(childId + 可选 diagnosisId/iepId,seq 自动取 max+1)、GET/{id}、GET ?childId=(列周期)、POST/{id}/close。
- 行级 AccessGuard(经 childId)。

**B. 训练数据上传(复用多模态)**
- TrainingRecord 四件套 + 端点 POST `/api/training-cycles/{id}/records`:
  multipart(mediaType + file + noteText)+ scores(JSON)。noteText 出网脱敏硬阻断 → MediaRecognizer 识别 → 落 transcript + scores。
- GET 列本周期训练数据。

**C. 阶段评估 + 纵向对比**
- StageEvalService(领域):取本周期训练数据的指标得分汇总 + 上一周期(seq-1)的 stage_eval 得分 →
  算 delta、达标率、未达标项(量化部分,纯 Java 计算,不依赖 AI)→ 拼 AI prompt(本期/上期得分 + 训练表现 + RAG 个案)→
  AiGateway 产「能力提升表现/未达标训练/方案适配性建议(有效保留/低效优化)」叙述草案。
- StageEvalAppService + 端点 POST `/api/training-cycles/{id}/stage-eval`(生成)、PUT 编辑、POST finalize、GET、PDF。
- 对比是「本周期 vs 上一周期」按 seq 关联,系统自动记录每周期评估,可回看历史。

**D. 适配性 → 新 IEP**
- 阶段评估定稿后,端点 POST `/api/training-cycles/{id}/next-iep`:
  把适配性建议(保留有效/优化低效)+ 原 IEP + 诊断维度交 IepService 产新一版 IEP DRAFT(复用本期 IEP 链路,prompt 加「在原方案基础上据阶段评估调整」),教师定稿。
- 新 IEP 关联回 cycle(或开新周期 seq+1,形成「评估→优化→再训练」循环)。

## 前端(frontend/)
- TrainingView.vue(或并入诊断工作台):建/选周期 → 上传训练数据(多模态 + 指标得分录入)→ 生成阶段评估(展示 delta 量化 + AI 叙述)→ 定稿 → 据适配性生成新 IEP。
- 纵向对比视图:同一 child 多周期得分趋势(简单表格/折线,展示各指标随阶段变化)。
- api/training.js;导航加「训练评估」入口(评估干预工作台)。

## 取舍点(默认取前者)
- 指标得分来源:复用量表 ScaleItem(itemId 对齐) vs 自定义训练项(自由 JSON)。默认两者都支持(scores JSON 存 itemId 或自定义名 + score + maxScore)。
- 新 IEP 关联:复用 iep.diagnosis_id + 新增 iep.cycle_id vs 仅文本带入。默认加 iep.cycle_id(可空)记录来源周期。
- 对比基线:严格 seq-1 相邻周期 vs 任选两周期对比。默认相邻(seq-1),前端可后续扩任选。

## 实施步骤(分多次提交,每步带测试)
1. **schema + 周期/训练数据/阶段评估表**:training_cycle/training_record/stage_eval 三表 + iep.cycle_id。
2. **周期管理 + 训练数据上传**:TrainingCycle/TrainingRecord 四件套 + AppService + Controller,复用 MediaRecognizer + 脱敏。测试:建周期/挂多模态训练数据识别/指标得分落库/行级权限。
3. **阶段评估 + 纵向对比**:StageEval 四件套 + Service(量化 delta 计算 + AI 叙述)+ AppService + Controller + PDF。测试:delta 计算、达标判定、AI 叙述生成 DRAFT、定稿冻结、跨周期对比。
4. **适配性 → 新 IEP**:next-iep 端点 + IepService prompt 扩「据阶段评估优化」。测试:据适配性产新 IEP 草案、关联周期。
5. **前端**:TrainingView + 纵向对比 + api + 导航。前端 build。
6. **联调 + 文档**:端到端(建周期→训练数据→阶段评估→对比→新IEP);全量 clean install + 前端 build;CLAUDE_CHANGES.md + INTEGRATION.md 阶段 I。

## 验证
- 后端每步 ./backend/mvnw test;全量 clean install;前端 npm run build。
- 端到端:同一 child 跑两个周期,验证 delta 量化对比 + AI 适配性建议 + 新 IEP 生成,覆盖脱敏硬阻断/DRAFT 冻结/行级权限。

## 不做(本期外)
- 真实向量检索(沿用关键词分类);真实视频关键帧抽取(沿用首版);任选两周期对比(默认相邻)。
- 趋势图高级可视化(首版简单表格/基础折线)。

## 风险
- delta 计算依赖指标得分结构化录入;若教师只传多模态不录得分,量化部分退化为空,AI 叙述仍可产(降级)。
- 周期/评估链路较长(周期→训练→评估→新IEP→新周期),分 6 步提交,每步独立可测。
- AI 适配性建议是辅助,人工定稿把关(沿用红线)。

