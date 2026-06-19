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
