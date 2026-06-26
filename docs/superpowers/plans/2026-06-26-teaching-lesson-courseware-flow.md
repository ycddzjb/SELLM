# 计划:教案两步生成(提示词→教案)+ 课件基于教案 + 草稿不落库 + 教学档案

> worktree dev_workspace。教学模块微服务 sellm-agent-teaching + 前端 + Python。

## 决策(已确认)
- 教案模块:残障类型下加**年龄区间**;内容与要求后加**「生成提示词」**按钮(AIGC 出提示词,可编辑)→ **「生成教案」**据提示词生成教学设计方案(可编辑)。
- **草稿不落库、不可导出**;**定稿保存**后才落库存档 + 导出(教案=Word,课件=PPT)。markdown 去掉。
- 新增**「生成课件」**:基于**选定的已定稿教案**生成 PPT 课件草稿(不落库不可导出)→ 定稿后落库 + 导出。
- 数据管理平台新增**「教学档案」**页:列出本人所有已落库(FINALIZED)的教案 + 课件,可查看/导出。

## 现状(需改的关键点)
- 后端 `generate`:**生成即落 DRAFT**(先 save 再出网)。新需求要无状态生成(不落库),定稿才入库 → 语义反转。
- Controller `/api/teaching/contents`:POST生成/PUT编辑/POST finalize/GET/GET list。
- 前端 TeachingContentPanel:三子功能统一8字段(PLAN/LESSON/COURSEWARE);TeachingView 4tab(训练方案/教案/课件/习题)。
- exporter:Word/PPT/Markdown(markdown 去掉)。
- Python teaching.py:task=plan/lesson/courseware/exercise。

## 范围界定
- **本期重点改 教案(LESSON) + 课件(COURSEWARE)** 的两步生成 + 草稿不落库流程。
- 训练方案(PLAN)/习题(EXERCISE):沿用原"生成即DRAFT→编辑→定稿"(不强制改;若一并改无状态可后议)。→ 为最小风险,**本期 PLAN/EXERCISE 不动**,仅 LESSON/COURSEWARE 走新流程。
- 课件 tab 取消(改为教案详情内"生成课件")?→ 需求(3)说"基于选定教案生成课件",课件不再是独立录入。**TeachingView 去掉课件 tab**,课件在教案定稿后从教案详情触发。

<!-- PLACEHOLDER -->

## 后端改动(sellm-agent-teaching)

### 新增无状态生成端点(不落库,只返回文本)
- `POST /api/teaching/draft/prompt`:入参 {contentType, title, options, requirement, subjectNames} → 脱敏→Python(task=`<type>_prompt` 或带 mode=prompt)→还原 → 返回 {prompt} 文本。**不落库**。
- `POST /api/teaching/draft/content`:入参 {contentType, prompt(可编辑后的提示词), title, options, subjectNames} → 脱敏→Python 据 prompt 生成→还原 → 返回 {content}。**不落库**。
- `POST /api/teaching/draft/courseware`:入参 {lessonContent(选定教案正文), title, options, subjectNames} → Python 生成 PPT 课件文本 → 返回 {content}。**不落库**。
- 实现:抽 `SmartLayerClient.generatePrompt(type, requirement, options)` 与现有 generateContent;或复用 generateContent 传 mode。AppService 加 `genPromptDraft/genContentDraft/genCoursewareDraft`(只脱敏→出网→还原,不碰 repo)。

### 定稿落库端点(一次性存 FINALIZED)
- `POST /api/teaching/contents/finalize`:入参 {contentType, title, options, content, sourceLessonId?(课件关联教案)} → 直接 save 一条 status=FINALIZED 的 TeachingContent。返回落库记录。
- 复用现有 edit(已落库后再改)+ get + list。
- teaching_content 加列 `source_id BIGINT`(课件记来源教案 id,可空);options 仍存选项。

### 其余
- `generate`(原生成即DRAFT)保留给 PLAN/EXERCISE;LESSON/COURSEWARE 前端改走新端点。
- list 默认仍按 owner+type;教学档案页用 listByOwnerAndType 取 LESSON/COURSEWARE 的 FINALIZED(前端过滤 status 或后端加过滤)。

## Python(teaching.py)
- 加 `lesson_prompt`/`courseware_prompt` 任务(或 generateContent 带 mode=prompt):据 8 字段+年龄区间+提问示例,产"提示词"文本(一段给模型的高质量 prompt)。
- 加 courseware 基于 lessonContent 生成 PPT 文本(已有旧 courseware 分支可复用思路:按页【标题】+【要点】+【配图建议】)。
- lesson/courseware prompt 读新增 ageRange 字段。

## 前端
- `teachingMeta.js`:加 AGE_RANGES(如 3~6岁/7~12岁/13~15岁,可输)。
- `TeachingContentPanel.vue`(LESSON 专用流程,或新建 LessonPanel):
  - 表单:标题/残障类型(多选可输)/教学学段/**年龄区间**/教学领域/教学场景/教学形式/内容与要求 + 提问示例。
  - 「生成提示词」按钮 → 调 draft/prompt → 显示可编辑提示词框。
  - 「生成教案」按钮(据提示词)→ 调 draft/content → 显示可编辑教案草稿。草稿**无导出按钮**。
  - 「定稿保存」→ 调 contents/finalize 落库 → 之后显示「导出 Word」「生成课件」。
  - 「生成课件」(教案定稿后)→ 调 draft/courseware(传教案正文)→ 课件草稿可编辑(无导出)→「课件定稿」落库(source=教案id)→「导出 PPT」。
- `exporter.js`:去掉 markdown 导出按钮(函数可留,不挂);教案 Word、课件 PPT。
- `TeachingView.vue`:tab = 训练方案/教案/习题(**去课件 tab**,课件在教案内生成)。
- 导出:草稿态不渲染导出按钮(仅 FINALIZED 后出现)。

## 数据管理平台:教学档案
- 新建 `views/TeachingArchiveView.vue` + 路由 `/teaching-archive`:
  - 调 listContents('LESSON') + listContents('COURSEWARE'),过滤 status=FINALIZED,合并列表(类型/标题/时间)。
  - 行操作:查看内容、导出(教案 Word / 课件 PPT)。
- MainLayout 数据管理平台加菜单「📁 教学档案」(教师可见)。
- `api/teaching.js` 加新端点函数:genPromptDraft/genContentDraft/genCoursewareDraft/finalizeNew。

## 实施步骤
1. 后端:teaching_content 加 source_id;AppService 加无状态 genXxxDraft + finalizeNew;Controller 加 /draft/prompt、/draft/content、/draft/courseware、/contents/finalize;SmartLayerClient 加 generatePrompt。测试(无状态不落库、finalize落库、课件source)。
2. Python:加 prompt 生成 + courseware-from-lesson + ageRange。py_compile。
3. 前端:teachingMeta AGE_RANGES;教案流程改两步+草稿不落库不导出+定稿后导出/生成课件;TeachingView 去课件tab;teaching.js 新端点。build。
4. 教学档案页 + 导航 + 路由。build。
5. 联调+文档。

## 验证
- 后端 sellm-agent-teaching test;前端 build;端到端:教案 生成提示词→生成教案→定稿→导出Word→生成课件→课件定稿→导出PPT;教学档案列已落库教案/课件。

## 不做(本期外)
- PLAN/EXERCISE 改无状态(沿用原DRAFT流程);markdown导出;课件独立录入tab;教学档案跨用户(仅本人)。

## 风险
- 草稿不落库=纯无状态出网接口,与现有"先落DRAFT"模式并存,需清晰区分两套(LESSON/COURSEWARE新 vs PLAN/EXERCISE旧)。
- 两步生成(提示词→教案)多一次出网,提示词与教案都需脱敏硬阻断。
- 课件定稿需带 source 教案id;教案删除/未定稿时课件入口不可用。
- teaching_content 加 source_id 列,dev H2 需重建。

