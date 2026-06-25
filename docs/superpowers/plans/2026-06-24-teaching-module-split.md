# 计划:教学训练 Agent 拆分「训练 + 教学(教案/课件/案例/习题)」

## 决策(已确认)
- 范围:四子功能(教案/课件/案例/习题)全做 + 教学/训练模块拆分。
- 导出:**前端纯 JS**(docx 生成 Word、pptxgenjs 生成 PPT、file-saver 下载),不动后端、不外联。

## 现状
- agent-teaching 现有:LessonPlan(教案,基于 IEP)、Courseware(课件,基于定稿教案)——**这其实偏「训练方案」语义**。需求把它归入「训练」模块。
- 新增「教学」模块四子功能:教案/课件/案例/习题,各有残障类型/领域/形式/学科/题型等选项,生成→编辑→导出。
- Python invoke_teaching 现按 task 分 lesson_plan/courseware;需扩 task 类型。
- 前端无 docx/pptxgenjs(需 npm i)。

## 架构简化(关键)
四子功能同构(输入要求+选项 → AIGC 文本 → 可编辑 → 导出),**不建四套表**,用统一 `teaching_content` 模型承载:
- 字段:id, owner_id, content_type(LESSON/COURSEWARE/CASE/EXERCISE), title, options(JSON:残障类型/领域/形式/学科/题型/难度/学段/方向等), ai_draft, content, status, created_at。
- 一套四件套(实体/Mapper/XML/Repository)+ 一个 TeachingContentAppService + 一个 Controller(按 type 分端点或统一端点带 type)。
- 现有 LessonPlan/Courseware **保留不动**(归「训练」模块,前端"训练方案"tab 仍用),新功能走新模型,互不影响。

<!-- PLACEHOLDER -->

## 实施步骤

### A. 后端 — 统一 teaching_content 模型 + 端点
- A1. schema.sql 加 `teaching_content` 表(字段见上;options 存 JSON 字符串)。
- A2. 四件套:`TeachingContent` 实体 + `TeachingContentMapper`+xml + `TeachingContentRepository`。
- A3. `TeachingContentAppService.generate(userId, type, title, requirement, options, subjectNames)`:
  落 DRAFT → 脱敏(subjectNames)→ Python(task=type,带 options+requirement)→ 还原 → update。
  + edit/finalize/get/list(按 type 过滤),复用现有 ownerId 行级校验范式。
- A4. `TeachingContentController` `/api/teaching/contents`:POST 生成、GET 列表(?type=)、GET/{id}、PUT/{id} 编辑、POST/{id}/finalize。
- A5. 出网脱敏:subjectNames 入屏蔽表(同现有 agent 范式),失败硬阻断。

### B. Python 智能层 — 四类生成 prompt
- `invoke_teaching` 扩 task:lesson/courseware/case/exercise,各按 options 拼专业 prompt:
  - lesson/courseware:残障类型+教学领域+教学形式+(标题/文本/章节/大纲/文档)。
  - case:残障类型+教学学科 → 教学案例。
  - exercise:残障类型+题型+难度+学段+出题方向 → 训练题(图文适配提示)。
- 默认 mock;配 key 走真实 LLM(已接通义千问)。

### C. 前端 — 教学训练页改版(训练 + 教学四 tab)
- C1. `npm i docx pptxgenjs file-saver`(锁版本)。
- C2. TeachingView 改为大 tab:**训练方案**(现有 LessonPlan/Courseware 流程不动)+ **教案** + **课件** + **案例** + **习题**。
- C3. 四个教学子页同构组件:选项表单(残障类型/领域/形式 或 学科 或 题型/难度/学段/方向,按 type 显示不同选项)+ 要求输入 + 生成 + 列表 + 编辑 + 定稿。
- C4. 导出工具 `utils/exporter.js`:`exportWord(title, content)`(docx)、`exportPpt(title, content)`(pptxgenjs,按段落/标题切片成页),"导出 Word"/"一键 PPT" 按钮。
- C5. 选项数据:教学领域/学科/题型/难度/学段/出题方向常量(meta 或新 teachingMeta.js)。

### D. 验证 + 文档
- 后端:teaching_content 生成/编辑/定稿测试 + 脱敏硬阻断;全量 clean install。
- 前端 build;端到端:四类各生成一条 + 导出 Word/PPT 可下载。
- 更新 CLAUDE_CHANGES.md + INTEGRATION.md。

## 选项枚举(需求明确)
- 残障类型:复用 DisorderType 8 类。
- 教学领域:运动能力/语言沟通/认知理解/社会交往/生活自理/行为管理。
- 教学形式:一对一教学/集体课堂教学。
- 教学学科(案例):生活语文/生活数学/生活适应/社交沟通/精细动作。
- 题型/难度/学段/出题方向(习题):题型(选择/填空/问答/看图等)、难度(易/中/难)、学段(学前/小学/初中)、方向(词汇拓展/语言理解/叙事表达等)。

## 不做(本期外)
- 文档上传解析作为生成输入(需求提"文档"输入,首版按文本粘贴;文件解析复用 qa DocAnalyzer 后续接)。
- 图文适配的真实配图(习题"图文适配"首版为文本描述+排版提示;真实生成图复用万相后续接)。
- PPT 精美模板(首版标题+要点切页的基础排版)。

## 风险
- 工作量大(后端新模型+Python+前端5tab+导出),分多步提交。
- pptxgenjs/docx 是较大前端依赖,锁版本、确认 build 体积可接受。
- 现有 LessonPlan/Courseware 保留,前端"训练"tab 复用其 api,不破坏已验证功能。

