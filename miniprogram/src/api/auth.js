import { request } from '../utils/http'

/** 登录,返回 { token, role, username, orgId, orgName }。 */
export function login(username, password) {
  return request({ url: '/api/auth/login', method: 'POST', data: { username, password } })
}

/** 微信登录:用 wx.login 取得的 code 换 token。 */
export function wechatLogin(code) {
  return request({ url: '/api/auth/wechat-login', method: 'POST', data: { code } })
}
