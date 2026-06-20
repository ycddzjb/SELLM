/**
 * uni-app 封装 request — 统一拦截、JWT 注入、错误处理。
 * 基地址通过环境变量或 manifest.json 注入(P0 占位)。
 */
const BASE_URL = 'http://localhost:8888' // 网关地址(dev)

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
        if (res.data.code === 0) {
          resolve(res.data.data)
        } else if (res.data.code >= 2000 && res.data.code < 3000) {
          userStore.logout()
          uni.navigateTo({ url: '/pages/login/login' })
          reject(res.data)
        } else {
          uni.showToast({ title: res.data.message || '请求失败', icon: 'none' })
          reject(res.data)
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络错误', icon: 'none' })
        reject(err)
      },
    })
  })
}
