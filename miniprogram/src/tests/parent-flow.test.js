/**
 * 小程序家长端关键路径自动化测试(uni-automator + jest)。
 *
 * ⚠️ 需「微信开发者工具」开启服务端口 + 后端网关在线 + 可用账号,方能运行(见 automator.config.js)。
 * 本机/CI 无该环境时整文件会因连不上工具而失败/超时 —— 预期行为,非代码问题。
 *
 * 运行:npm run test:mp-weixin
 * 约定:`program` 为 uni-automator 注入的全局(IDE 自动化驱动)。
 */
/* eslint-disable no-undef */

// 测试账号(按 seed / 真实环境调整)
const TEST_USER = process.env.MP_TEST_USER || 'parent_demo'
const TEST_PWD = process.env.MP_TEST_PWD || 'secret123'

describe('家长端关键路径', () => {
  jest.setTimeout(60000)

  it('登录页可输入并提交,成功后进入孩子列表', async () => {
    const page = await program.reLaunch('/pages/login/login')
    await page.waitFor(500)

    const inputs = await page.$$('input')
    expect(inputs.length).toBeGreaterThanOrEqual(2)
    await inputs[0].input(TEST_USER)
    await inputs[1].input(TEST_PWD)

    const loginBtn = await page.$('.btn')
    await loginBtn.tap()
    await page.waitFor(1500)

    // 登录成功应 reLaunch 到孩子列表
    const cur = await program.currentPage()
    expect(cur.path).toContain('pages/children/children')
  })

  it('孩子列表页加载(列表或空态二选一渲染)', async () => {
    const page = await program.reLaunch('/pages/children/children')
    await page.waitFor(1200)
    const cards = await page.$$('.card')
    const empty = await page.$('.empty')
    // 有孩子→卡片;无孩子→空态。至少一种存在
    expect(cards.length > 0 || empty !== null).toBe(true)
  })

  it('文生素材:提交后出现任务状态区(轮询中或终态)', async () => {
    const page = await program.reLaunch('/pages/aids/asset')
    await page.waitFor(800)

    const ta = await page.$('.ta')
    await ta.input('为自闭症儿童设计认识情绪的绘本')
    const submit = await page.$('.btn')
    await submit.tap()
    await page.waitFor(2000)

    // 提交后应出现当前任务卡片(PENDING/RUNNING/SUCCESS/FAILED 任一)
    const statusEl = await page.$('.t-status')
    expect(statusEl).not.toBeNull()
  })

  it('教具推荐页按障碍类型渲染卡片或空态', async () => {
    const page = await program.reLaunch('/pages/aids/recommend')
    await page.waitFor(1200)
    const aids = await page.$$('.aid')
    const empty = await page.$('.empty')
    expect(aids.length > 0 || empty !== null).toBe(true)
  })
})
