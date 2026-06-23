/**
 * uni-automator + jest 配置(mp-weixin 自动化测试)。
 *
 * 用 @dcloudio/uni-automator 提供的 jest 环境/全局 setup/teardown 驱动微信开发者工具:
 *   - globalSetup(index.js):编译并通过 cli 启动开发者工具、建立自动化连接,注入全局 program
 *   - testEnvironment(environment.js):每个测试文件注入 program/page 等全局
 *   - globalTeardown(teardown.js):断开/关闭
 *
 * 运行前提见 TESTING.md(开发者工具开启服务端口 + AppID + 后端在线)。
 * 平台/配置经环境变量传入:UNI_PLATFORM=mp-weixin、UNI_AUTOMATOR_CONFIG=automator.config.js。
 */
const path = require('path')

process.env.UNI_PLATFORM = process.env.UNI_PLATFORM || 'mp-weixin'
process.env.UNI_AUTOMATOR_CONFIG =
  process.env.UNI_AUTOMATOR_CONFIG || path.resolve(__dirname, 'automator.config.js')

module.exports = {
  // environment.js 自身经 UNI_AUTOMATOR_CONFIG 启动开发者工具并注入全局 program(无需 globalSetup)
  testEnvironment: '@dcloudio/uni-automator/dist/environment.js',
  testEnvironmentOptions: {
    compile: true,            // 由 automator 负责编译 mp-weixin 产物
  },
  testTimeout: 120000,
  testMatch: ['<rootDir>/src/tests/**/*.test.js'],
  watchman: false,
}
