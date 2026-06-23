#!/usr/bin/env node
/**
 * 用微信开发者工具自带的 node.exe 直接跑 cli.js auto(绕开 Node≥20 spawn .bat EINVAL)。
 * 前提:开发者工具已开「服务端口」(设置→安全设置→服务端口),且 GUI 在运行、已登录。
 *
 * 用法:node scripts/wx-auto.cjs            # 在 9420 起 automation,供 jest 连接
 *      node scripts/wx-auto.cjs --check    # 仅检测 .ide-status 是否 On
 */
const { spawn } = require('child_process')
const fs = require('fs')
const path = require('path')
const os = require('os')

const DEVTOOLS = process.env.WX_DEVTOOLS_DIR
  || 'D:/Program Files (x86)/Tencent/微信web开发者工具'
const NODE_EXE = path.join(DEVTOOLS, 'node.exe')
const CLI_JS = path.join(DEVTOOLS, 'cli.js')
const PROJECT = process.env.WX_PROJECT
  || path.resolve(__dirname, '..', 'dist', 'dev', 'mp-weixin')
const PORT = process.env.WX_AUTO_PORT || '9420'

function findIdeStatus() {
  const base = path.join(os.homedir(), 'AppData', 'Local', '微信开发者工具', 'User Data')
  if (!fs.existsSync(base)) return null
  for (const d of fs.readdirSync(base)) {
    const f = path.join(base, d, 'Default', '.ide-status')
    if (fs.existsSync(f)) return fs.readFileSync(f, 'utf8').trim()
  }
  return null
}

if (process.argv.includes('--check')) {
  const st = findIdeStatus()
  console.log('.ide-status =', st === null ? '(not found)' : st)
  process.exit(st === 'On' ? 0 : 2)
}

if (!fs.existsSync(NODE_EXE) || !fs.existsSync(CLI_JS)) {
  console.error('找不到开发者工具 node.exe/cli.js:', DEVTOOLS)
  process.exit(1)
}

console.log(`[wx-auto] node.exe cli.js auto --project ${PROJECT} --auto-port ${PORT}`)
const child = spawn(NODE_EXE, [CLI_JS, 'auto', '--project', PROJECT, '--auto-port', PORT], {
  stdio: 'inherit',
})
child.on('exit', (code) => process.exit(code || 0))
