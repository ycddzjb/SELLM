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
