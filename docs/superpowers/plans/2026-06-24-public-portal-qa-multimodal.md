# 计划:公开门户落地页 + 问答机器人豆包式对话(含多模态文档分析)

## 目标
1. **未登录可访问**:做公开落地页,任意用户(含匿名)看到五大 Agent 介绍卡片 + 问答体验入口;问答匿名可直接用。
2. **豆包/千问式问答页**:对话式 AIGC 问答(已有纯文本 RAG)+ **上传多模态文档(图片/PDF/文本)做分析**(后端新建能力)。

## 决策(已确认)
- 范围:公开页 + 问答匿名可用;教学/评估/教具/科研(涉儿童 PII)仍需登录。
- 多模态:连后端一起做(QA 加文件上传+解析+多模态模型)。
- 匿名会话:**不落库历史**,只返本次答案(前端内存保留本次对话);登录用户照旧落库。

## 现状(已探查)
- 前端路由守卫:非 /login /register 一律跳登录(`router/index.js beforeEach`)。
- 网关白名单:仅 `/api/auth/`、`/actuator/health`(`gateway application.yml`)。
- QA 后端:`/api/qa/ask` 用 `requireUser(X-User-Id)` 强制登录;纯文本 RAG,无文件能力。Python `invoke_qa` 只收 question/topK。
- 多模态模型 `MultimodalModel` 在 **backend**(非 common),qa 模块无此依赖。

## 红线影响与处置
- **认证红线**:仅放开 `/api/qa/ask`(+ 新文档分析端点)到网关白名单 + 网关对白名单放行匿名;**其余 4 Agent 端点不动**,行级权限 AccessGuard 不动。匿名请求网关不注入 X-User-Id,QA 端点改为「userId 可空 = 匿名」。
- **脱敏红线**:匿名问答/文档同样走出网前脱敏(anonymize 失败硬阻断),不变。
- **PII**:匿名不落库;文档分析默认 mock 不外联,真分析需配 multimodal key。

<!-- PLACEHOLDER_PLAN -->

## 实施步骤

### A. 后端 — QA 匿名 + 多模态文档分析

**A1. QA ask 支持匿名**
- `QaController.ask`:userId 可空(去掉 requireUser 硬拦);`QaAppService.ask` 中 userId==null 走匿名分支:不建会话/不落库,直接脱敏→Python→还原返回答案。
- 登录分支不变(落库会话+消息)。

**A2. 多模态文档分析端点(新)**
- `QaController` 加 `POST /api/qa/analyze`(multipart:file + 可选 question),userId 可空(匿名可用)。
- 新 `DocAnalyzeAppService`:读文件字节 → 出网前脱敏(文本部分)→ 调多模态模型 → 返回分析文本。
- **多模态能力获取**:qa 模块新增依赖 backend 已有的 `MultimodalModel`?它在 backend 不在 common。两个方案:
  - 方案 i(推荐,改动小):qa 内**自建一个轻量 `DocAnalyzer` 适配器**(默认 mock 返回占位分析;provider=openai 时走 OpenAI 兼容 vision/文档 API),镜像 backend `OpenAiVisionModel` 的 `protected send` 范式,强制 HTTP/1.1。不污染 backend。
  - 方案 ii:把 `MultimodalModel` 抽到 common-* 供两边共享(改动大,影响 backend)。
  - **采方案 i**。
- 文档解析:图片直接送 vision;PDF/文本先抽文本(纯文本拼进 prompt)。首版:图片走 vision mock、文本类读 UTF-8 文本拼 prompt;PDF 解析标注为后续(或用轻量库)。
- 红线:文件/文本出网前脱敏,失败硬阻断;默认 mock 不外联。

**A3. Python 智能层(可选增强)**
- 现 `invoke_qa` 够用(纯文本)。多模态分析走 qa 后端的 DocAnalyzer 直接出网(不绕 Python),与 backend vision 一致。

### B. 网关 — 放行匿名问答

- `gateway application.yml` 白名单加 `/api/qa/ask`、`/api/qa/analyze`。
- 确认 JwtAuthGatewayFilter 白名单放行时不强制注入 X-User-Id(匿名即不注入);登录用户带 token 仍注入。
- 限流:匿名按 IP 限流(现有 fallback 已支持)。

### C. 前端 — 公开落地页 + 豆包式问答页

**C1. 路由守卫放开公开页**
- `router beforeEach`:`/landing`、`/chat`(公开问答)加入免登白名单(同 /login)。
- 新增公开路由(不经 MainLayout 鉴权布局):`/landing`(落地页)、`/chat`(公开问答页)。

**C2. 公开落地页 `LandingView.vue`**
- 五大 Agent 介绍卡片(复用 Dashboard 卡片样式),顶部「登录」按钮。
- 问答卡片「立即体验」→ /chat;其余四卡「登录使用」→ /login。

**C3. 豆包式问答页 `ChatView.vue`**(公开 + 登录通用)
- 大对话区:消息气泡(用户/AI)、流式感(逐条追加)、来源/深链展示。
- 底部输入框 + **文件上传按钮**(图片/PDF/文本),上传后附在消息上,调 `/api/qa/analyze`。
- 纯文字提问调 `/api/qa/ask`。
- 匿名:本地内存保留对话(刷新即清);登录:可存历史(走现有 conversations)。
- 顶部:未登录显「登录」入口,登录显用户名。

### D. 验证 + 文档
- 后端:qa 新增匿名 ask 测试 + analyze 端点测试(mock multimodal,脱敏硬阻断)。
- 全量 `mvn clean install` 绿;前端 build OK。
- 端到端:匿名(无 token)经网关 /api/qa/ask 200;/api/qa/analyze 上传图片返分析。
- 更新 INTEGRATION.md + CLAUDE_CHANGES.md。

## 工作量与风险
- 后端:QaController/AppService 改 + 新 DocAnalyzeAppService + DocAnalyzer 适配器 + 端点 + 测试(中)。
- 网关:白名单 2 行(小,但属安全面,需测匿名放行不破坏其他鉴权)。
- 前端:LandingView + ChatView + 路由守卫改 + 公开布局(中大)。
- 风险:① 网关放行匿名须确保只放 qa 这两个端点,不波及其他;② PDF 解析首版可能仅占位;③ ChatView 是新写 UI,需你实际验。

## 不做(本期外)
- 真流式(SSE/WebSocket)token 级流式:首版整条返回,前端模拟逐条。
- PDF 深度解析(表格/图文混排):首版纯文本抽取或占位。
- 匿名会话持久化。

