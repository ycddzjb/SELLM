# 端到端联调验证结果(Task 7)

日期:2026-06-19 · 分支:`feat/admin-frontend`

后端 dev profile(H2 文件库,种子 admin/admin123 + 机构1 阳光小学 + CARS 量表)起在 `localhost:8080`,用 curl 直连走完整链路验证前后端契约。前端 build 已在各任务(Task 2–6B)分别用 `npm run dev`/`npm run build` 验证可编译;本次自动化联调用 curl 直连后端,浏览器人工验收见末尾清单。

## 后端启动

`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` → `Started SellmApplication` / `Tomcat started on port 8080`,约 6 秒。

## curl 链路逐步实际结果

| # | 步骤 | 实际返回(摘要) | 结论 |
|---|------|------------------|------|
| 1 | 登录 `POST /api/auth/login` | `code:"0"`,`data.token`(JWT,196 字符),`data.role:"MANAGER"` | 符合 |
| 2 | 建档 `POST /api/children`(小红/ASD) | `code:"0"`,`data:2`(childId) | 符合 |
| 3 | 列档案 `GET /api/children` | `code:"0"`,数组含 `{id,name:"小红",disorderType:"ASD",orgId:1,guardianUserId:null}` —— **姓名明文**(后端 AES 解密读回) | 符合 |
| 4 | 提交评估 `POST /api/assessments`(cars,q1=2,q2=3) | `code:"0"`,`data:{id:2,totalScore:5.0,bandLabel:"轻-中度",interpretation:"建议进一步评估"}` | 符合 |
| 5 | 生成报告 `POST /api/reports` | `code:"0"`,`data:{id:2,draft:"[AI草稿] …小红(阳光小学)…轻-中度…",finalizedContent:null,status:"DRAFT"}` | 符合 |
| 6 | 生成 IEP `POST /api/ieps` | `code:"0"`,`data:{id:2,draft:"[AI草稿] …长短期目标与干预活动建议…",finalizedContent:null,status:"DRAFT"}` | 符合 |
| 7 | 按 child 列历史(Task 1B 端点) | `GET /api/assessments?childId=2`、`/api/reports?childId=2`、`/api/ieps?childId=2` 各返回 `code:"0"` + 1 条数组 | 符合 |
| 8 | 建老师 `POST /api/users`(role TEACHER) | `code:"0"`,`data:3`(userId) | 符合 |

**结论:全部符合预期。** 验证了 dev 后端 + 全链路(登录→建档→评估→报告→IEP)+ JWT 注入 + 行级机构归属 + Child 姓名加密落库读回明文 + 按 child 历史回看,在真实 HTTP 运行时成立;前端 `api/` 各模块契约(路径/方法/请求体/`Result{code,message,data}` 解包)与后端一致。

## 联调中发现并修复的问题(dev 配置 bug)

`application-dev.yml` 的 `spring.sql.init` 缺 `encoding`,导致 Windows 平台默认 GBK 读取 UTF-8 的 `seed-dev.sql`,CARS 量表中文文案(`bandLabel`/`interpretation`)落库乱码(首轮 curl `bandLabel` 读回为 `锟斤拷` 类乱码)。已加 `encoding: UTF-8`,因种子用 `MERGE … KEY(id)` 幂等,重启即覆盖修正,二轮 curl 读回 `轻-中度`/`建议进一步评估` 正常。非前后端契约不匹配,属 dev 种子加载配置缺陷。

> 注:首轮 curl 建档/评估等返回 400,是 Git Bash 在中文 Windows 下把 `小明` 以非 UTF-8 字节发出(`Invalid UTF-8 middle byte 0xf7`)所致,后端正确拒绝;改用 UTF-8 文件 `--data-binary @file` 提交后全部通过。属测试环境字符集问题,非后端 bug。

## 待人工验收清单(浏览器)

起前端 `cd frontend && npm run dev`(:5173),按序人工核对:

1. 打开 `http://localhost:5173`,用 `admin/admin123` 登录 → 进入后台,角色 MANAGER。
2. 儿童档案页:看到既有档案 / 新建一条档案(填姓名、障碍类型),列表显示明文姓名。
3. 点某档案"评估"→ 带入 childId,按 CARS 答题提交 → 返回总分与分段(bandLabel)。
4. "生成报告"→ 展示 AI 草稿、可编辑、定稿(status 由 DRAFT → 定稿)。
5. "生成 IEP"→ 展示草案、编辑、定稿。
6. 回档案点"详情"进工作台 → 该童的评估/报告/IEP 历史均列出,可查看/续作。
7. 用户管理页建一个 TEACHER 账号。
8. 登出 → 用新老师账号登录 → 验证只能看本机构(orgId=1)档案(行级权限)。

红线提示:报告/IEP 页应明示"AI 草稿仅供参考、需人工把关",定稿为人工编辑后提交。

## 联调完毕

后端进程已停,端口 8080 已释放;未起前端 dev server(5173 未占用)。H2 文件库(`backend/data/`)已 gitignore,不提交。


---

# 四级权限体系端到端联调结果(Task 9)

日期:2026-06-18 · 分支:`feat/rbac-v2`

后端 dev profile(H2 文件库 `backend/data/asd_dev.mv.db`)起在 `localhost:8080`,用 `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` 启动(日志见 `Tomcat started on port 8080` / `Started SellmApplication`)。DevSeeder 幂等纠正生效:旧库中 admin 启动后即为 SUPER_ADMIN(无机构、ACTIVE)。curl 直连走四级权限关键路径,逐条核对。机构名用英文(StarCenter/Nanjing)规避 Git Bash 中文 UTF-8 编码坑。

## 关键路径逐条结果(全部符合预期)

| # | 路径 | 预期 | 实际 | 结论 |
|---|------|------|------|------|
| 1 | 超管登录 `POST /api/auth/login` admin/admin123 | role=SUPER_ADMIN | `role:"SUPER_ADMIN"`,orgId=null,uid=1,签发 token | 通过 |
| 2 | 超管建机构 `POST /api/orgs` StarCenter/Nanjing | 200 建成功 | `code:0`,data=2(机构 id) | 通过 |
| 3 | 超管为机构1建 MANAGER `POST /api/users` orgId=1 | 200 建成功 | `code:0`,data=14(用户 id) | 通过 |
| 4 | MANAGER 登录 + 建 TEACHER | 登录成功 + 建成功 | role=MANAGER orgId=1;建 TEACHER data=15 | 通过 |
| 5 | MANAGER 建 MANAGER `POST /api/users` role=MANAGER | 403(业务拒) | 403 | 通过 |
| 6 | 公开机构列表 `GET /api/orgs/public`(免登录) | 200 含机构 | 返回阳光小学(1)、StarCenter(2) | 通过 |
| 7 | 家长公开注册 `POST /api/auth/register` orgId=1 | 返回 id,PENDING | `code:0`,data=16 | 通过 |
| 8 | 待审家长登录 | 400(待审核) | 400 | 通过 |
| 9 | MANAGER 看待审列表 `GET /api/users/pending` | 含 par_dev1 | data=[{id:16,par_dev1,PARENT,orgId:1,status:PENDING}] | 通过 |
| 10 | MANAGER approve id=16 + 家长再登录 | approve 200 + 登录 200 拿 token | approve=200;登录返回 role=PARENT + token | 通过 |
| 11 | TEACHER 改自己密码 + 新密码登录 | change 200 + 新密码登录 200 | change=200;tea456 登录=200 | 通过 |

## 安全红线核对

- 端点级 RBAC + 行级双层:MANAGER 建 MANAGER 被业务逻辑拒(403,#5);仅 MANAGER 可见待审列表(#9)。
- status 强制:公开注册家长落 PENDING、审核前登录被拒(400,#8);approve 转 ACTIVE 后方可登录(#10)。
- 用户列表/待审列表响应不含 passwordHash(#9 JSON 仅 id/username/role/orgId/status)。
- 自助改密码改的是 token 持有者本人(#11)。
- 超管跨机构:admin orgId=null,可建任意机构的 MANAGER(#3)。

## 联调结论

四级权限(SUPER_ADMIN / MANAGER / TEACHER / PARENT)升级后契约全连通,权限拦截正确,11 条关键路径全部符合预期,未发现越权未拦或契约不符。后端进程已停,端口 8080 已释放;未起前端 dev server(5173 未占用)。H2 文件库已 gitignore,不提交。

---

# 阶段 A(组织模型 + 班级)端到端联调结果(计划六 Task 7)

日期:2026-06-20 · 分支:`feat/org-class`

后端 dev profile(H2 文件库,已含 teacher_class 新表,种子 admin/admin123)起在 `localhost:8080`,curl 直连走阶段 A 完整链路。前端 `npm run build` 已通过(Task 6)。

## curl 链路逐步实际结果

| # | 步骤 | 实际返回(摘要) | 结论 |
|---|------|------------------|------|
| 1 | 超管登录 `POST /api/auth/login` | `code:"0"`,JWT token(190 字符) | 符合 |
| 2 | 超管一体建机构+管理员 `POST /api/orgs`(disorderTypes=ASD,ADHD / province=Jiangsu / city=Nanjing / managerUsername+managerPassword) | `code:"0"`,`data:2`(orgId) | 符合 |
| 3 | 管理员登录 | `code:"0"`,`role:"MANAGER"`,`orgId:2`,`orgName:"E2E Special Edu Center"` | 一体创建的管理员可登录,机构正确 |
| 4 | 管理员建班级 `POST /api/classes`(disorderTypes=ASD,LANGUAGE) | `code:"0"`,`data:1`(classId) | 符合 |
| 5 | 班级列表 `GET /api/classes` | `[{id:1,name:"E2E Class A",orgId:2,disorderTypes:"ASD,LANGUAGE"}]` | orgId 自动本机构,多选障碍类型存取一致 |
| 6 | 管理员建老师绑班级 `POST /api/users`(role=TEACHER,classIds=[1]) | `code:"0"`,`data:7`(teacherId) | 老师绑班级成功 |
| 7 | 本机构家长列表 `GET /api/users/parents` | `[{id:8,username:parent_...,role:PARENT,orgId:2,status:ACTIVE}]` | 只返回本机构 PARENT |
| 8 | 越权:建老师绑他机构班级 classIds=[99999] | HTTP **403**,且 hacker_t 未落库 | 行级校验生效,@Transactional 回滚 |
| 9 | 老师调 `GET /api/users/parents` | HTTP **403** | 端点级拦截(仅 MANAGER) |

## 联调结论

阶段 A(组织模型 + 班级)契约全连通:一体建机构+管理员(orgId 自动归属)、班级 CRUD(orgId 自动本机构、障碍类型多选)、老师绑班级(本机构校验、越权 403 且事务回滚)、管理员查本机构家长(仅 PARENT、老师越权 403)。9 条路径全部符合预期。后端进程已停,端口 8080 已释放;H2 文件库已 gitignore,不提交。

---

# 阶段 B(量表库管理)端到端联调结果

日期:2026-06-20 · 分支:`feat/org-class`

后端 dev profile(H2 文件库,scale/scale_item 已 ALTER 新增列)起在 `localhost:8080`,curl 走量表库 CRUD + 动态量表评估全链路。前端 `npm run build` 已通过(Task 5/6)。

## curl 链路逐步实际结果

| # | 步骤 | 实际返回(摘要) | 结论 |
|---|------|------------------|------|
| 1 | 超管创建量表 `POST /api/scales`(感统,3题+2分段) | `code:"0"`,`data` 返回 scaleId | 符合 |
| 2 | 按品类过滤 `GET /api/scales?disorderType=SENSORY_INTEGRATION` | 含新建 scaleId | 符合 |
| 3 | 详情 `GET /api/scales/{id}` | items 数=3 | 符合 |
| 4 | 更新 `PUT /api/scales/{id}`(改名 v2 + 加第4题) | HTTP 200,items 数变 4 | 整体替换正确 |
| 5 | 老师/管理员 `GET /api/scales` 与详情 | HTTP 200 | authenticated 可读(评估需要) |
| 6 | 老师 `POST /api/assessments`(用新量表4题各2分=8) | `code:"0"`,totalScore=8,bandLabel="dysfunction" | 动态量表计分正确 |
| 7 | 超管 `DELETE /api/scales/{id}` | HTTP 200 | 符合 |
| 8 | MANAGER `POST /api/scales` | HTTP 403 | 端点级拦截(写仅超管) |

## 联调结论

阶段 B(量表库管理)契约全连通:超管对量表的增删改查(含题目/分段整体替换)、按品类过滤、已登录用户可读量表定义、老师用动态创建的量表完成评估并正确计分、写操作仅超管。8 条路径全部符合预期。后端进程已停,端口 8080 已释放;H2 文件库已 gitignore,不提交。

---

# 阶段 C(家长注册改造 + 老师审核)端到端联调结果

日期:2026-06-20 · 分支:`feat/org-class`

后端 dev profile(H2 文件库,已新增 parent_profile 表、放开 child.name_enc 非空)起在 `localhost:8080`,curl 走家长注册→老师审核→建儿童全链路。前端 `npm run build` 已通过(Task 6)。

## curl 链路逐步实际结果

| # | 步骤 | 实际返回(摘要) | 结论 |
|---|------|------------------|------|
| 1 | 超管建机构+管理员 | orgId=5 | 符合 |
| 2 | 管理员建班级 | classId=2 | 符合 |
| 3 | 管理员建老师绑班级 | teacherId=15,绑 class 2 | 符合 |
| 4 | 公开查机构班级 `GET /orgs/public/{id}/classes` | 含 class 2(免登录) | 符合 |
| 5 | 公开查班级老师 `GET /classes/public/{id}/teachers` | 含该老师(免登录,仅 id+username) | 符合 |
| 6 | 家长注册(指派该老师) | `code:"0"`,parentId=16 | 落 app_user+parent_profile |
| 7 | 家长审核前登录 | HTTP 400 | PENDING 不能登录 |
| 8 | 老师看待审 `GET /users/pending` | 含该家长,带姓名/儿童/关系标签(母子)/班级名 | 行级:仅分派给自己的 |
| 9 | 超管 approve | HTTP 403 | 端点级仅 TEACHER |
| 10 | 非指派老师 approve | HTTP 403 | 行级:仅指派老师 |
| 11 | 指派老师 approve | `code:"0"` | 审核通过 |
| 12 | 家长审核后登录 | HTTP 200 | ACTIVE 可登录 |
| 13 | 家长看自己孩子 `GET /children` | Child(name=ChildA,orgId=5,guardianUserId=16) | 通过时建档案+guardian 关联,行级可见 |
| 14 | 管理员看本机构家长 `GET /users/parents` | 完整字段(姓名/儿童/关系标签/班级名) | 符合 |

> 注:Chinese PII 加密/解密由后端单元测试覆盖(curl 联调用 ASCII 名避开 Windows Git Bash 的 UTF-8 编码坑)。

## 联调结论

阶段 C(家长注册改造 + 老师审核)契约全连通:公开级联查询(机构→班级→老师)、家长注册落两表(姓名/儿童姓名加密)、审核归口到注册所选老师(端点级 TEACHER + 行级指派校验,超管/非指派老师均 403)、审核通过自动建儿童档案并关联监护家长、家长审核后可登录并按行级权限看到自己孩子、管理员查看本机构家长完整信息。14 条路径全部符合预期。后端进程已停,端口 8080 已释放;H2 文件库已 gitignore,不提交。

---

# 阶段 D 批一(儿童档案扩展 + 成长记录)端到端联调结果

日期:2026-06-20 · 分支:`feat/org-class`

后端 dev profile(H2,child 加 6 列、放开 name_enc 非空、新增 child_log 表)起在 `localhost:8080`。前端 `npm run build` 已通过(Task 4)。

## curl 链路逐步实际结果

| # | 步骤 | 实际返回(摘要) | 结论 |
|---|------|------------------|------|
| 1 | 老师建档带扩展字段 | childId=3 | 符合 |
| 2 | 读回档案 | baseline/monthlyGoal/reassessDate(2026-10-01)/iepDueDate/进度 全回显 | 扩展字段持久化正确 |
| 3 | 改档(加年度IEP+改复评) | HTTP 200,annualIepSummary=annual-X | 整体替换更新(未传字段置空) |
| 4 | 加三类记录 | 共 3 条 | 符合 |
| 5 | 按 type 过滤 | CLASSROOM_TRACK 1 条,logTypeLabel=课堂追踪 | 过滤 + 中文标签正确 |
| 6 | 他机构老师加记录 | HTTP 403 | 行级(AccessGuard) |
| 7 | 非法 logType | HTTP 400 | 校验生效 |
| 8 | 家长(审核建档)的孩子 id | =4 | 审核建档关联正确 |
| 9 | 家长对自己孩子加记录 | HTTP 200 | 家长可写自己孩子 |
| 10 | 家长对别人孩子加记录 | HTTP 403 | 行级拦截 |
| 11 | 老师删本机构儿童记录 | HTTP 200 | 符合 |

## 联调结论

阶段 D 批一(儿童档案扩展字段 + 成长记录)契约全连通:儿童档案 6 个结构化扩展字段(基线/年度IEP/月度目标/复评时间/IEP到期/干预进度)建档与改档读回一致;child_log 三类记录(课堂追踪/家校沟通/阶段复盘)CRUD + type 过滤 + 中文标签;记录读写复用 AccessGuard 行级(他机构老师 403、家长仅限自己孩子、非法类型 400)。11 条路径全部符合预期。后端进程已停,端口 8080 已释放;H2 文件库已 gitignore,不提交。

> 注:儿童姓名仍 AES 加密落库(扩展字段为非 PII 概要,明文);中文 PII 加密由后端单元测试覆盖,curl 用 ASCII 名避开 Windows Git Bash UTF-8 编码坑。

---

# 阶段 D 批二(到期提醒)端到端联调结果

日期:2026-06-20 · 分支:`feat/org-class`

后端 dev profile 起在 `localhost:8080`,curl 验证到期提醒计算与行级过滤。前端 `npm run build` 已通过(Task 2)。

## curl 链路逐步实际结果

| # | 步骤 | 实际返回(摘要) | 结论 |
|---|------|------------------|------|
| 1 | 老师建 4 档:近期复评(+15天)/逾期IEP(-8天)/超窗(+50天)/无日期 | 建档成功 | — |
| 2 | GET /api/children/reminders | 仅 2 条:OverdueKid(IEP_DUE,daysLeft=-8,overdue=true)排首、NearKid(REASSESS,daysLeft=15,overdue=false) | 临期+逾期纳入、超窗/无日期排除、按 daysLeft 升序 |
| 3 | 他机构老师 GET reminders | data=[] | 行级过滤(AccessGuard)生效 |

## 联调结论

阶段 D 批二(到期提醒)契约连通:后端正确算出 30 天内(含已逾期)需复评/IEP到期的儿童,逾期 overdue=true 且 daysLeft 为负,超窗与无日期不纳入,按紧急度(daysLeft 升序)排序;行级权限复用 AccessGuard,他机构老师看不到。3 条路径全符合预期。后端进程已停,端口 8080 已释放。

至此**阶段 D(儿童档案大扩展)全部完成**(批一档案字段+成长记录,批二到期提醒)。

---

# 阶段 E(真实大模型接入 + PDF + 家庭 IEP)端到端联调结果

日期:2026-06-20 · 分支:`feat/org-class`

后端 dev profile(provider=mock,**不外联**)起在 `localhost:8080`。前端 `npm run build` 已通过(Task 5)。

## curl 链路逐步实际结果

| # | 步骤 | 实际返回(摘要) | 结论 |
|---|------|------------------|------|
| 1 | 老师 评估 | assessment id | — |
| 2 | 生成报告 | 草稿含 `[AI草稿]` 前缀 | Mock 模型生效(默认不外联) |
| 3 | 报告未定稿下载 PDF | HTTP 400 | 仅定稿可下载 |
| 4 | 报告定稿后下载 PDF | HTTP 200,application/pdf,`%PDF` | PDF 导出正确 |
| 5 | 基于定稿报告生成 IEP | 草稿含 `[AI草稿]` | Mock 生效 |
| 6 | IEP 定稿后下载 PDF | HTTP 200,`%PDF` | 符合 |
| 7 | 家长家庭IEP(报告未定稿) | HTTP 400 | 需先有定稿评估报告 |
| 8 | 家长对自己孩子家庭IEP(已定稿报告) | status=DRAFT | 取最新定稿报告生成成功 |
| 9 | 家长对别人孩子家庭IEP | HTTP 403 | 行级(AccessGuard) |

## 联调结论

阶段 E 契约全连通:可切换 AI 适配器默认 Mock(provider=mock,不外联,真实模型需显式配置 api-key 才启用,出网内容经网关脱敏);报告/IEP 仍走"AI 草稿→人工定稿"红线;定稿后可下载 PDF(未定稿 400);家长家庭 IEP 取该儿童最新定稿评估报告 + 家长目标生成,行级仅限自己孩子(无定稿报告 400、他人孩子 403)。9 条路径全部符合预期。后端进程已停,端口 8080 已释放。

**至此路线图 A→B→C→D→E 全部完成。** 真实模型联调需在用户自有环境配置 SELLM_AI_PROVIDER=openai + SELLM_AI_BASE_URL + SELLM_AI_API_KEY 后验证(本期交付可切换骨架,Mock 全绿)。多模态识别为后续独立阶段。

---

# 真实大模型联调结果(通义千问 qwen-plus)

日期:2026-06-20 · 分支:`fix/ai-http-timeout`(基于 master)

通过环境变量切到真实 provider(`SELLM_AI_PROVIDER=openai` + dashscope 兼容端点 + qwen-plus),不改任何代码默认值、key 不落盘。dev 库,curl 走真实生成全链路。

## 联调结果

| 链路 | 实际返回 | 结论 |
|---|---|---|
| 评估报告生成 | HTTP 200,~22s,真实四节结构化报告(无 [AI草稿] Mock 标记) | 真实模型生效 |
| IEP 生成(基于定稿报告) | HTTP 200,~45s,真实长短期目标+干预活动 | 真实模型生效 |
| 家庭 IEP(家长,自己孩子,已有定稿报告) | HTTP 200,~43s,真实家庭训练计划 | 真实模型生效 |
| 脱敏 | 出网经网关替换为 [儿童1]/[学校1] 占位符(报告草稿首次以"儿童1"呈现即证模型仅见占位符),还原后给授权用户展示真名 | 脱敏红线在真实模型上确认 |

## 联调暴露并修复的缺陷

1. **HTTP/2 协商卡死**:JDK HttpClient 默认 HTTP/2 与 dashscope 协商时请求 30s 超时(curl 直连 0.6s 成功)→ 强制 HTTP/1.1。
2. **超时偏紧**:默认 30s,而真实长报告生成实测 20-45s → 默认提至 60s(`SELLM_AI_TIMEOUT` 可配),connectTimeout 与请求超时分离。

## 结论

阶段 E 的真实大模型接入端到端验证通过:可切换适配器在配置真实 provider 后,报告/IEP/家庭IEP 全部由通义千问真实生成,脱敏出网+还原链路正确,人工定稿+PDF 下载红线不变。修复后全量回归 205 测试全绿(默认 mock 不受影响)。

> 安全提醒:联调用的 API key 已出现在协作记录中,建议轮换。生产用 SELLM_AI_* 环境变量注入,key 不入库不入代码。

---

# 阶段 F(多模态评估)端到端联调结果

日期:2026-06-20 · 分支:`feat/multimodal-eval`

后端 dev profile(multimodal=mock + 存储=noop,**默认不外联、不依赖 MinIO**)起在 `localhost:8080`。前端 `npm run build` 已通过。

## curl 链路逐步实际结果

| # | 步骤 | 实际返回 | 结论 |
|---|------|----------|------|
| 1 | 老师上传训练笔记(NOTE) | mediaId 返回 | 上传落库 |
| 2 | analyze 识别 | 每指标 Mock 建议(q1/q2 各 2.0 + 理由) | 多模态识别闭环(默认 Mock) |
| 3 | 采纳建议分提交正式评估 | totalScore=4,bandLabel=轻-中度 | 建议→现有计分链路打通 |
| 4 | 他机构老师上传 | HTTP 403 | 行级权限(AccessGuard) |

> 图片文件上传经 MockMvc `MockMultipartFile` 测试验证(存储 noop 往返 + analyze 出建议);curl 直传图片受 Windows Git Bash multipart 编码限制未走通,非后端问题。

## 联调结论

阶段 F 多模态评估最小闭环连通:素材(图片/笔记)上传 → 对象存储(默认本地 noop)→ 多模态模型(默认 Mock,不外联)出各指标评分建议 → 老师采纳填入 → 走现有评估计分。红线:默认不外联(multimodal mock + storage noop);真实 vision/MinIO 需显式配置;图像 PII 风险在配置项/.env.example 显著告知;AI 仅产建议、老师确认;行级权限贯穿。后端全量回归 225 测试全绿(默认 mock+noop)。后端进程已停,端口已释放。

> 真实多模态联调需用户配 SELLM_MULTIMODAL_PROVIDER=openai + base-url + api-key(如通义 qwen-vl-plus),并确认已获监护人对儿童影像出网的知情同意。
