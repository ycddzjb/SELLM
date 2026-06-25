# 微信登录端到端联调 Runbook

> 本质是人工 QA + 真实凭据,无法在开发机/CI 自动跑通。本文档给出可照做的步骤、检查项与排错。
> 涉及代码:后端 `AuthController.wechatLogin` / `WeChatClient`(jscode2session)、小程序 `pages/login` + `api/auth.wechatLogin`、管理端「微信家长待激活」。

## 0. 前置准备
- 微信小程序 **AppID + AppSecret**(微信公众平台 → 开发管理 → 开发设置)。
- 已部署可公网/内网访问的后端 + 网关(网关 `:8888`,后端 `agent-assessment`)。
- 微信开发者工具 + 一个微信账号(用于真机/模拟器登录)。

## 1. 配置
后端环境变量(见 `.env.example`):
```
SELLM_WECHAT_APP_ID=wx<你的AppID>
SELLM_WECHAT_APP_SECRET=<你的AppSecret>
SELLM_WECHAT_DEFAULT_ORG_ID=        # 可留空:新家长落「未分配机构」,由管理者认领
```
小程序:
- `miniprogram/src/manifest.json` 的 `mp-weixin.appid` 填同一 AppID。
- `miniprogram/src/utils/http.js` 的 `BASE_URL` 指向网关(默认 `http://localhost:8888`;真机需可达地址 + 已在微信后台配「request 合法域名」HTTPS)。
- 微信后台 → 开发设置 → **服务器域名**:把网关域名加入 request 合法域名(真机必须 HTTPS;开发者工具可临时勾「不校验合法域名」)。

## 2. 联调步骤
1. 起后端 + 网关,确认 `GET {网关}/actuator/health`(白名单)200。
2. 微信开发者工具导入 `miniprogram`,编译预览。
3. 登录页点「微信一键登录」:
   - 小程序 `uni.login` 取 `code` → POST `/api/auth/wechat-login {code}`。
   - **首次**:后端 `WeChatClient.openidByCode` 用 code 换 openid → 建 PENDING 家长(用户名 `wx_<openid>`)→ 因非 ACTIVE 返 400「账号待审核」。小程序 toast 提示。
4. 管理端(MANAGER,本机构)→ 用户管理 → 「微信家长待激活」:
   - 列表应出现该 `wx_<openid>` 账号(机构「未分配」或本机构)。
   - 点「激活」→ 填孩子姓名 / 障碍类型 / 班级(可空)→ 确认 → 后端建 Child + ParentProfile + 落本机构 + 置 ACTIVE。
5. 小程序再点「微信一键登录」:
   - 同一 openid → `findByOpenid` 命中已 ACTIVE 账号 → 返 token → reLaunch 进孩子列表。
   - 孩子列表应显示步骤 4 建的孩子。

## 3. 检查项(逐条勾)
- [ ] 首登返 400 且管理端能看到该待激活微信家长。
- [ ] 激活后 `app_user.status=ACTIVE`、`org_id` 落本机构、`wx_openid` 绑定、建了 `child`(guardian=该家长)。
- [ ] 再次微信登录拿到 token,`role=PARENT`。
- [ ] 孩子列表 / 家庭 IEP / 报告 / 教具 / 文生素材 各页经网关带 `Authorization` 正常取数。
- [ ] 他机构 MANAGER 看不到 / 激活不了该家长(行级 403)。

## 4. 排错
| 现象 | 排查 |
|---|---|
| 返「微信登录未配置」 | `SELLM_WECHAT_APP_ID/SECRET` 未注入后端 |
| 返「微信登录失败: ...」 | code 过期(60s)/被用过;或 AppID/Secret 不匹配;看微信 errmsg |
| 小程序请求被拦截 | 微信后台未配 request 合法域名(真机);或开发者工具未勾「不校验合法域名」 |
| 首登未出现在待激活列表 | 确认 MANAGER 机构;未分配机构(org null)也应可见(`findPendingWeChat` 含 `org IS NULL`) |
| 激活后仍登不上 | 确认 status 已 ACTIVE;token 是否带上;网关 JWT 验签密钥与后端一致(`SELLM_JWT_SECRET`) |
| 真机连不上后端 | BASE_URL 必须 HTTPS 且公网可达;localhost 真机不可达 |

## 5. 本环境状态(如实)
- 当前开发机**无 AppID/AppSecret、无真机、后端未公网部署**,故未实际跑通端到端。
- 已用 MockMvc + stub(`WeChatLoginApiTest` 4 + `WeChatActivateApiTest` 10)覆盖后端全链路逻辑:换 openid(stub)、首登建 PENDING、激活建档落机构、再登 ACTIVE 返 token、行级权限、各校验边界。
- 真实 jscode2session 出网(`WeChatClient.send`)与真机交互需按本 Runbook 人工验证。
