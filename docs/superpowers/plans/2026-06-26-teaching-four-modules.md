# 计划:智能教学 Agent 精简为四模块 + 三子功能表单重构

> worktree dev_workspace。教学模块在微服务 `sellm-agent-teaching/`(非 backend 单体),前端 `frontend/`,Python `ai-smart-layer/`。

## 决策(已确认)
- 教学 Agent 精简为四模块:**训练方案 / 教案 / 课件 / 习题**(去掉「案例」CASE)。
- **训练方案纳入统一模型**:新增 content_type=PLAN,与教案/课件同构;下线旧 IEP 版训练方案 tab。
- 训练方案/教案/课件 **三子功能**用统一新表单(8 字段);**习题不改字段**(保留题型/难度/学段/方向)。
- 提问模板/示例:**前端内置常量**(teachingPresets.js,仿 qaPresets);后续可换后端。
- 选项数据:**前端常量 + 可选可输**(filterable allow-create);学段细化 学前/小学1-6/初中7-9。
- 生成内容可编辑落库归档 + 导出 **Word/PPT/Markdown**(markdown 新增)。

## 三子功能(PLAN/LESSON/COURSEWARE)新表单字段
1. 标题(自由输入)
2. 内容与要求(textarea + 提问模板下拉填充 + 提问示例点击直接生成)
3. 教学学段 stage(学前 / 小学1-6 / 初中7-9,单选)
4. 残障类型 disorderTypes(**多选 + 可输**,DISORDER_TYPES)
5. 教学学科 subject(**可选可输**,TEACHING_SUBJECTS)
6. 教学领域 field(**可选可输**,TEACHING_FIELDS)
7. 教学场景 scene(家庭 HOME / 机构 CENTER / 学校 SCHOOL)
8. 教学形式 form(一对一 ONE_ON_ONE / 集体 GROUP)

习题保持:标题 / 残障类型 / 题型 / 难度 / 学段 / 出题方向 / 内容与要求(本期不动)。

<!-- PLACEHOLDER -->

## options 存储约定(统一)
- options JSON 改为**存 label 中文 + 多值用数组**,Python 直接用文本拼 prompt(不再 code→label 转换):
  `{ disorderTypes: ["孤独症","智力障碍"], subject:"生活语文", field:"语言沟通", scene:"学校", form:"一对一", stage:"小学3年级" }`。
- 残障类型多选 → 数组;其余单值字符串。可输时直接存用户输入文本。
- 习题 options 不变(disorderType 单值 + questionType/difficulty/stage/direction)。

## 后端改动(sellm-agent-teaching)
- A. `TeachingContentAppService.java:24` TYPES:`Set.of("PLAN","LESSON","COURSEWARE","EXERCISE")`(去 CASE,加 PLAN)。
- B. `TeachingContentMapper.xml` update **补 options**(现仅更 title/aiDraft/content/status):编辑重生成时选项才落库。需 Mapper 接口/Repository update 带 options;若本期编辑不改选项,可暂不动(标注)。→ 本期生成即定选项,编辑只改正文,**update 暂不含 options**(与现状一致,够用)。
- C. schema 注释更新 content_type 枚举(PLAN/LESSON/COURSEWARE/EXERCISE);旧 CASE 行软处理(不删表)。
- 不动旧 LessonPlan/Courseware 表与 Controller(保留代码,前端不再挂入口;避免连带破坏)。

## Python 改动(ai-smart-layer/app/agents/teaching.py)
- `_teaching_prompt`:删 case 分支;加 plan 分支;lesson/courseware/plan 读新字段(disorderTypes 多值/subject/field/scene/form/stage)。
- task 集合 `:53`:`("plan","lesson","courseware","exercise")`(去 case 加 plan)。
- disorder 多值:opts.disorderTypes 是数组(label),join 顿号;兼容旧 disorderType 单值。
- plan/lesson/courseware prompt 拼入 学段/场景/学科/领域/形式;plan 输出按【训练目标】【训练步骤】【训练频次】【评估方式】等。
- exercise 分支不变。

## 前端改动
- D. `teachingMeta.js`:STAGES 改为 学前 + 小学1-6 + 初中7-9(细化);TEACHING_SUBJECTS/FIELDS/FORMS 保留(作可输下拉选项);加 SCENES(家庭/机构/学校)。
- E. 新建 `api/teachingPresets.js`:TEACHING_TEMPLATES(`[{title,template}]` 带占位)+ TEACHING_EXAMPLES(字符串数组),仿 qaPresets。
- F. `utils/exporter.js` 加 `exportMarkdown(title, content)`(拼 `# title\n\n content` 存 .md)。
- G. `TeachingContentPanel.vue` 重构:
  - 新增 props 区分"三子功能(PLAN/LESSON/COURSEWARE)用 8 字段"vs"习题(EXERCISE)用原字段"。
  - 三子功能表单:标题 / 内容与要求(+模板下拉 + 示例 chips)/ 学段 / 残障类型(multiple filterable allow-create)/ 学科(filterable allow-create)/ 领域(filterable allow-create)/ 场景 / 形式。
  - buildOptions:三子功能产 {disorderTypes:[], subject, field, scene, form, stage};习题维持原样。
  - 导出按钮加「导出 Markdown」。
  - 提问示例点击 → 填入 requirement 并直接 onGenerate;模板点击 → 填入 requirement 待补全。
- H. `TeachingView.vue`:tab 改为 训练方案(PLAN,挂 Panel)/ 教案(LESSON)/ 课件(COURSEWARE)/ 习题(EXERCISE);**删案例 tab**;**删旧 IEP 训练方案内联表单**(train tab 改挂 `<TeachingContentPanel type="PLAN"/>`)。
- I. `api/teaching.js`:统一 generateContent/editContent/finalizeContent/listContents 已够用(训练方案走 PLAN type);旧 generatePlan/Courseware 等可保留不删(无入口)。

## 实施步骤(分步,带验证)
1. 后端:TYPES 去CASE加PLAN;schema 注释;编译 + TeachingContentApiTest 调整(CASE→PLAN 用例)。
2. Python:teaching.py 删case加plan + 三子功能读新字段;py_compile。
3. 前端:teachingMeta(学段/场景)+ teachingPresets + exporter markdown;TeachingContentPanel 重构(8字段+模板示例+md导出);TeachingView 改4tab去案例去旧train表单。build。
4. 联调 + 文档:端到端(PLAN/LESSON/COURSEWARE 新表单生成、习题不变、导出md);CLAUDE_CHANGES + INTEGRATION。

## 验证
- 后端 ./backend/mvnw -pl sellm-agent-teaching test(注意 worktree 用系统/backend wrapper)。
- 前端 npm run build。
- 端到端:四 tab 各生成、三子功能新字段进 options、习题原样、Word/PPT/MD 导出。

## 不做(本期外)
- 旧 LessonPlan/Courseware 表与 Controller 物理删除(保留无入口,避免连带);习题字段重构;提问模板后端化;学科按障碍适配映射;options 编辑重生成落库(本期生成即定)。

## 风险
- 训练方案从旧 IEP 体系切到 PLAN:旧 train tab 的"基于定稿IEP生成"能力前端下线(后端代码留存);若仍需"基于IEP"入口需另议。
- options 多选数组 + 可输:Python 拼 prompt 要兼容数组/字符串/旧单值 disorderType。
- 残障类型多选可输:存 label 数组,出网经脱敏(label 非 PII,安全)。
- 教学模块测试 TeachingContentApiTest 含 CASE 用例需改 PLAN。

