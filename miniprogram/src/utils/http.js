/**
 * uni-app 封装 request — 统一拦截、JWT 注入、错误处理。
 * 以 HTTP statusCode 为主:2xx 取 Result.data;401 登出跳登录;其余 toast message。
 * 后端 Result.code 为字符串("0" 成功),兼容业务错误码。
 */
// 网关地址(dev):生产/联调走网关 8888;无网关(仅起 backend)时可临时指向 8080
// 支持环境变量覆盖,自动化测试时设 SELLM_MP_BASE_URL=http://localhost:8080
const BASE_URL = (typeof process !== 'undefined' && process.env && process.env.SELLM_MP_BASE_URL)
  || 'http://localhost:8888'

import { useUserStore } from '../store/user'

export function request(options) {
  const userStore = useUserStore()

  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        ...(userStore.token ? { Authorization: `Bearer ${userStore.token}` } : {}),
        ...options.header,
      },
      success: (res) => {
        const status = res.statusCode
        const body = res.data || {}
        if (status === 401) {
          userStore.logout()
          uni.reLaunch({ url: '/pages/login/login' })
          reject(body)
          return
        }
        if (status >= 200 && status < 300) {
          // Result 信封:code 为字符串 "0" 成功
          if (body.code === undefined || body.code === '0' || body.code === 0) {
            resolve(body.data !== undefined ? body.data : body)
          } else {
            uni.showToast({ title: body.message || '请求失败', icon: 'none' })
            reject(body)
          }
        } else {
          uni.showToast({ title: body.message || `请求失败(${status})`, icon: 'none' })
          reject(body)
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络错误', icon: 'none' })
        reject(err)
      },
    })
  })
}
