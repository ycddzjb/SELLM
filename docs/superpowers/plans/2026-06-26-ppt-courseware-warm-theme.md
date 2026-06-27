# 计划:PPT 课件重构(LLM出结构JSON + 前端暖色渲染 + 去草稿直接落库)

> worktree dev_workspace。教学课件:Python(出PPT JSON)+ 前端(暖色pptxgenjs渲染/落库/档案下载)。

## 决策(已确认)
- PPT 生成:**LLM(qwen-plus)出结构化 PPT 内容 JSON** + 前端 pptxgenjs 按**暖色高对比主题**渲染(无外部文生PPT服务)。
- 落库:**存 LLM 产的结构 JSON**(轻),教学档案下载时前端现场渲染为 .pptx,可重复下载。
- 课件流程:**去草稿,点"生成课件"直接落库(FINALIZED)到教学档案**,不再草稿编辑定稿。
- 教案仍按现有逻辑(基本信息表头+分层+学情分析+纯文本Word)。

## PPT 内容 JSON 结构(LLM 产出)
```
{ "title": "课题名称",
  "slides": [
    {"type":"cover","title":"...","subtitle":"特教类型/学段/课时"},
    {"type":"section","heading":"学情分析","points":["A组...","B组...","C组..."],"group":"info"},
    {"type":"section","heading":"教学目标","points":[...]},
    {"type":"section","heading":"重难点","points":[...]},
    {"type":"section","heading":"教学准备","points":[...]},
    {"type":"section","heading":"教学过程·第一阶段:导入","points":[...]},  // 四阶段各一页
    ... (第二/三/四阶段),
    {"type":"section","heading":"分层评价","points":[...]}
  ] }
```
- 涵盖:封面 / 学情分析 / 教学目标 / 重难点 / 教学准备 / 教学过程(四阶段4页) / 分层评价。
- 每页 points 简洁;能力组别(A/B/C)用颜色编码,辅助梯度标注在要点内。

## Python(teaching.py)
- `courseware_from_lesson` 改为产 **PPT JSON**(非散文):prompt 要求严格输出上述 JSON(slides数组),据教案正文+授课对象特点组织页面;暖色/分层/图标的设计意图写进 points 文本(配色由前端主题统一)。
- 返回 {"content": <JSON字符串>}。容错:解析失败前端降级按散文渲染。

## 前端
### exporter.js renderCoursewarePptx(暖色主题)
- 新增 `exportCoursewarePptx(title, jsonOrText)`:解析 PPT JSON →pptxgenjs 渲染:
  - 暖色高对比主题:背景暖白(#FFF8F0),主色暖橙(#E8762C)/暖红辅助,标题深棕(#5A3A22)高对比。
  - 封面页:大标题+副标题(特教类型/学段/课时)+暖色色块。
  - section页:顶部色条+标题(图标emoji前缀:学情📋目标🎯重难点⭐准备🧰过程①②③④评价✅),要点逐条;
    A/B/C组要点用颜色编码(A绿/B橙/C红高对比)+组别标签。
  - 解析失败(非JSON):降级用旧 splitIntoSlides 文本切页。
- 旧 exportPpt 保留(兼容),课件走新 exportCoursewarePptx。

### LessonPanel 课件流程(去草稿直接落库)
- openCourseware(lesson)后:不再先 draftCourseware→草稿编辑;改为「生成并归档课件」一键:
  调 draftCourseware(lessonId)拿 PPT JSON → 直接 finalizeNew({contentType:'COURSEWARE',title:教案名+'·课件',content:JSON,sourceId:教案id}) → 提示已归档 → 关闭课件卡。
- 去掉 cwDraft 编辑框与"课件定稿保存"。

### TeachingArchiveView 下载
- 课件行"导出 PPT"改调 exportCoursewarePptx(row.title, row.content)(content 为 PPT JSON)。
- 查看课件:JSON 不直接展示原文,可展示"(PPT 内容,点导出生成)"或简单列页标题。

## 实施步骤
1. Python:courseware_from_lesson 改产 PPT JSON(含设计要求);py_compile;重建 smartlayer。
2. 前端 exporter:exportCoursewarePptx 暖色主题渲染(解析JSON+降级);build。
3. LessonPanel:课件一键生成直接落库(去草稿);api 复用 draftCourseware+finalizeNew;build。
4. TeachingArchiveView:导出PPT走新渲染;build。
5. 联调:教案→生成课件→教学档案出现→导出PPT暖色多页;文档。

## 验证
- 经网关:LESSON定稿后生成课件→COURSEWARE直接FINALIZED入档案;Archive导出.pptx含封面+各节暖色页。
- 前端 build;py_compile;smartlayer 重建。

## 不做(本期外)
- 真实配图(图标用emoji/色块,不插真实图片);PPT母版动画;外部文生PPT服务;课件二次编辑(去草稿后如需改,重新生成)。

## 风险
- LLM 产严格 JSON 可能偶发不合规→前端解析失败降级文本切页(保证不崩)。
- 暖色主题硬编码在前端渲染,设计统一;能力组别配色靠要点文本含"A组/B组/C组"关键词匹配。
- 课件去草稿:生成即落库,内容不满意需重新生成(可接受,PPT JSON 可重复生成)。
