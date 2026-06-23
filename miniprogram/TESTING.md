# 小程序家长端 自动化测试 / 微信真机联调

## 1. 自动化测试(uni-automator + jest)

文件:
- `automator.config.js` —— 开发者工具连接配置(端口/CLI 路径)
- `src/tests/parent-flow.test.js` —— 关键路径用例(登录 / 孩子列表 / 文生素材轮询 / 教具推荐)
- `package.json` 脚本:`npm run test:mp-weixin`

### 运行前提(缺一不可)
1. 安装并打开**微信开发者工具**;设置 → 安全 → 开启**服务端口**(允许 CLI/HTTP 自动化驱动)。
2. `src/manifest.json` 的 `mp-weixin.appid` 填入真实小程序 AppID(否则工具无法编译预览)。
3. 后端 + 网关在线(默认 `http://localhost:8888`),并准备可登录账号(设 `MP_TEST_USER` / `MP_TEST_PWD` 环境变量,默认 `parent_demo`/`secret123`)。
4. 按本机实际设开发者工具 CLI:`set WX_DEVTOOLS_CLI=C:/Program Files (x86)/Tencent/微信web开发者工具/cli.bat`。
5. `npm run test:mp-weixin`。

### 当前状态(如实说明)
- 本仓库/当前开发机**未安装微信开发者工具、无 AppID**,故 `test:mp-weixin` 只能完成编译,随后等待连接开发者工具服务端口而无法继续 —— **用例未在本环境运行**。
- 已验证:配置与用例 `node --check` 语法通过;`uni test` 编译阶段成功(产物 `dist/dev/mp-weixin`);构建 `build:mp-weixin` 不受影响。
- 待具备上述环境后,直接 `npm run test:mp-weixin` 即可驱动真机/模拟器跑通用例。

## 2. 微信登录端到端真机联调 Runbook

见同目录 `WECHAT_LOGIN_RUNBOOK.md`。
