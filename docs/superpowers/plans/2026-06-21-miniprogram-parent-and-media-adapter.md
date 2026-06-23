# 计划:小程序家长端 + 真实文生图/视频适配器(Python 智能层)

> 日期:2026-06-21 · 分支:feature/special-edu-llm(dev_workspace,延续当前)
> 两块独立交付:A 小程序家长端(全页面)· B 真实文生媒体适配器(Python 智能层)
> 平台红线见 [[sellm-ai-privacy-redlines]];状态见 [[special-edu-llm-p0-done]]

---

## A. 小程序家长端(uni-app + Vue3 + Pinia,编 mp-weixin)

### A0. 基础设施修复(先做,空壳从未联通)
- **重写 `src/utils/http.js`**:现用 `res.data.code === 0`(数字)判成功,但后端 `Result.code` 是字符串 `"0"` → 永不 resolve;登出逻辑用旧数字码 `>=2000`,现为 `C004` 等字符串。改为:以 HTTP `statusCode` 为主(2xx 成功取 `data`,401 登出跳登录,其余 toast `message`);兼容 `code !== '0'` 业务错误。基址 `http://localhost:8888`(网关)。
- **`src/store/user.js`** 扩 `role`,token 持久化到 `uni.storage`(`onLaunch` 恢复);`logout` 清 storage。
- **`src/api/`** 新增模块化封装:`auth.js`/`child.js`/`iep.js`/`report.js`/`aids.js`/`log.js`,各导出调 `request` 的函数。

### A1. 页面(pages.json 注册 + tabBar)
后端端点全部经网关 8888,带 `Authorization: Bearer <token>`(网关验签后注入 X-User-* 给下游)。

1. **login**(`pages/login/login`):POST `/api/auth/login` {username,password} → 存 token/role/username;非 PARENT 也可登录(提示家长端建议家长账号)。
2. **children**(`pages/children/children`,tabBar 首页):GET `/api/children` 列表卡片;顶部提醒条 GET `/api/children/reminders`(复评/IEP到期,逾期标红)。点卡片进详情。
3. **child-detail**(`pages/child/detail`):GET `/api/children/{id}`,分区展示基本信息(障碍类型/基线/月目标/进展)+ 入口按钮(家庭IEP/报告/成长记录)。
4. **family-iep**(`pages/family-iep/list` + `pages/family-iep/detail`):
   - list:GET `/api/family-ieps?childId=`;
   - 生成:POST `/api/family-ieps` {childId,parentGoal}(家长可发,路径落 `/api/**` authenticated)→ 草案;
   - detail:查看 draft/finalizedContent/status,FINALIZED 显示「下载PDF」(`/api/family-ieps/{id}/pdf`,小程序内 `uni.downloadFile` + `openDocument`)。
5. **report**(`pages/report/list` + `detail`):GET `/api/reports?childId=` 只读;FINALIZED 可下载 PDF(`/api/reports/{id}/pdf`)。家长不可生成(POST 限老师)。
6. **aids 教具**(tabBar):
   - **recommend**(`pages/aids/recommend`):按障碍类型下拉 → GET `/api/aids/recommendations?disorderType=`;卡片列表(名称/类别/适用类型/用法)。
   - **asset**(`pages/aids/asset`):选类型 + 填 prompt → POST `/api/aids/assets`(202+taskId)→ 轮询 GET `/api/aids/tasks/{taskId}`(PENDING/RUNNING→定时 setInterval,SUCCESS 显示产物/可预览 image、FAILED 显示 error);我的素材 GET `/api/aids/assets`。
7. **growth/log**(`pages/log/log`,从 child-detail 进):GET `/api/children/{childId}/logs` 列表;家长可 POST 添加(logType+content)。

### A2. tabBar
首页(children)/ 教具(aids recommend)/ 我的(占位:用户名+登出)。3 个 tab。

### A3. 公共
- `components/` 抽卡片/空态/加载;`utils/format.js`(障碍类型 label、状态映射)。
- `App.vue` `onLaunch` 恢复登录态;未登录拦截跳 login。

### A4. 验证
`npm run build:mp-weixin` 必须 DONE(改完前端必跑)。无法真机/微信开发者工具自动化,以编译通过 + 代码静态审查为准(本机已验证空壳可 build)。

---

## B. 真实文生图/视频适配器(Python 智能层)

### B1. Python 侧(可切换适配器,默认 mock 不外联,镜像 `llm.py`)
- **新增 `app/adapters/media.py`**:
  - `MediaGenerator` 抽象 `async generate(asset_type, prompt) -> {media_b64, mime_type, ext}`。
  - `MockMediaGenerator`(默认):按类型产**最小合法占位二进制** —— IMAGE/PICTUREBOOK→1×1 PNG;AUDIO→极简 WAV 头;VIDEO→占位(返 text-only 或最小 mp4 box,首版可降级为文本说明)。零外联、确定性。
  - `OpenAiImageGenerator`(provider=openai 且配 key 时):调图像生成 API,`protected _send()`/`async _send` 抽出便于测试子类注入假响应、不真连网;强制 HTTP/1.1 语义(httpx)。**仅当显式配 key 装配**。
  - `get_media_generator()` 按 `settings.media_provider` 选择。
- **`app/config.py`** 加 `media_provider="mock"` / `media_base_url` / `media_api_key` / `media_model` / `media_timeout`(env 前缀 SELLM_)。
- **`app/agents/aids.py`** 改:仍调 `get_llm()` 产文本描述,**再**调 `get_media_generator()` 产媒体;返回 `{content, media_b64?, mime_type?, ext?, mock}`。文本类(无媒体)时 media 字段缺省。
- **`tests/test_aids.py`** 加媒体路径断言(mock 返回非空 media_b64 + mime_type;占位符不还原)。

### B2. Java 侧(agent-aids,解码存盘)
- **`SmartLayerClient.generate` 返回类型** String → 新 `GeneratedContent`(text, byte[] media, mimeType, ext)。
- **`HttpAidsSmartLayerClient`** 解析 JSON:content + 可选 media_b64(Base64 解码)/mime_type/ext。
- **`AssetGenerationTask`** 改:脱敏 prompt → Python → 还原 text;**若 media 非空** → `storage.put("asset/{id}.{ext}", mediaBytes, mimeType)`;**否则** → 存 text 为 `.txt`(向后兼容)。状态机/硬阻断/兜底不变。
- **`AssetResponse`/`TaskStatusResponse`** 可加 `mimeType`(便于小程序预览判断 image)。
- **测试**:现有 stub 返回 text-only(仍 `.txt`,保持绿);新增 stub 返回 media → 断言存为对应 ext + mimeType;脱敏硬阻断仍 `called==false`。

### B3. 红线一致性
- prompt 仍在 Java 出网前 `Anonymizer` 脱敏,失败硬阻断绝不调 Python(不变)。
- 媒体生成器只接收**已脱敏文本 prompt**,不接触明文/图片(无新 PII 出网面)。
- 默认 mock 零外联;真实生成器仅显式配 key 时装配,出网即配置方自担合规(同 `MultimodalProperties` 注释惯例)。

### B4. 环境变量
`.env.example` 加 `SELLM_MEDIA_PROVIDER`/`SELLM_MEDIA_BASE_URL`/`SELLM_MEDIA_API_KEY`/`SELLM_MEDIA_MODEL`/`SELLM_MEDIA_TIMEOUT`(默认 mock)。

---

## 验证与收尾(质量门禁)
1. **后端**:`mvn clean install`(clean,避 stale 假绿)全 reactor 10 模块 SUCCESS;agent-aids 测试绿(现 15 + 新增媒体路径);backend242/qa16/teaching15/research20 不受影响。
2. **小程序**:`npm run build:mp-weixin` DONE。
3. **Python**:`py_compile` + AST parse;pytest 因本机无 3.11/未装走静态审查(模式镜像已验证的 llm/research)。
4. **变更记录**:按 `.claude/CLAUDE.md` 规则向 `.claude/CLAUDE_CHANGES.md` 追加 A、B 两条(格式:日期/类型/描述/位置/原因/影响/验证)。
5. **记忆**:更新 [[special-edu-llm-p0-done]](小程序家长端 ✓ + 文生媒体真实适配器 ✓)。

## 不做(YAGNI,首版外)
- 微信登录(code2session);用账号密码登录。
- 小程序自动化测试(uni-automator,无环境)。
- VIDEO 真实生成(占位/降级);真实图像生成只搭可切换骨架,默认 mock。
- 资源库/向量检索(Milvus)。
