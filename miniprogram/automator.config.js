/**
 * uni-app 自动化测试配置(mp-weixin)。
 *
 * ⚠️ 运行前提(本仓库 CI/本机当前无法满足,故 test 脚本默认不在 CI 跑):
 *   1. 安装并打开「微信开发者工具」,设置 → 安全 → 开启「服务端口」(CLI/HTTP 调用)。
 *   2. 配置微信小程序 AppID(manifest.json 的 mp-weixin.appid),否则工具无法编译预览。
 *   3. 后端 + 网关已起(默认 http://localhost:8888),且有可用账号(见 seed)。
 *   4. 设置环境变量指向开发者工具 CLI:
 *        Windows: set UNI_AUTOMATOR_WS_ENDPOINT / 或在 wechat.devtools 填 cliPath
 *   5. 运行:npm run test:mp-weixin
 *
 * 无上述环境时测试会因连不上开发者工具而 SKIP/超时 —— 这是预期,非代码问题。
 */
module.exports = {
  // 仅 mp-weixin 端
  'mp-weixin': {
    // 微信开发者工具路径(本机已探测在 D 盘;executablePath 用于校验存在,cliPath 用于启动)
    // 可用环境变量 WX_DEVTOOLS_CLI 覆盖
    executablePath: process.env.WX_DEVTOOLS_CLI || 'D:/Program Files (x86)/Tencent/微信web开发者工具/cli.bat',
    cliPath: process.env.WX_DEVTOOLS_CLI || 'D:/Program Files (x86)/Tencent/微信web开发者工具/cli.bat',
    port: 9420,
    account: '',
    args: '',
    cwd: '',
    // Node ≥20 直接 spawn .bat 会 EINVAL;故 launch:false,由 scripts/wx-auto.cjs 用 node.exe 预启动 auto,
    // jest 仅连接已开的 9420 端口。需先在开发者工具开启「服务端口」。
    launch: process.env.WX_LAUNCH === '1',
    teardown: 'disconnect',
    remote: false,
  },
}
