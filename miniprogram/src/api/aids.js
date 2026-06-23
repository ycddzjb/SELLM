import { request } from '../utils/http'
import { useUserStore } from '../store/user'

const BASE_URL = (typeof process !== 'undefined' && process.env && process.env.SELLM_MP_BASE_URL)
  || 'http://localhost:8888'

/** 教具推荐(disorderType 可空 → 全部)。 */
export function recommendAids(disorderType) {
  const q = disorderType ? `?disorderType=${encodeURIComponent(disorderType)}` : ''
  return request({ url: `/api/aids/recommendations${q}` })
}

/** 提交文生素材任务 → { taskId }(202)。 */
export function submitAsset(type, prompt) {
  return request({ url: '/api/aids/assets', method: 'POST', data: { type, prompt } })
}

/** 轮询任务状态 → { status, result?, error? }。 */
export function pollAssetTask(taskId) {
  return request({ url: `/api/aids/tasks/${taskId}` })
}

/** 我的素材列表。 */
export function listAssets() {
  return request({ url: '/api/aids/assets' })
}

/**
 * 取素材图片并返回可供 <image> 显示的本地临时文件路径。
 * raw 端点需 JWT 鉴权(<image> 标签不带 Authorization 头),且真图较大(数百 KB,
 * base64 data URL 易超微信 <image> 渲染上限),故用 uni.downloadFile 带 token 下载为
 * 本地临时文件,无大小限制。行级权限由后端保证。
 */
export function fetchAssetImageUrl(assetId) {
  const userStore = useUserStore()
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: `${BASE_URL}/api/aids/assets/${assetId}/raw`,
      header: userStore.token ? { Authorization: `Bearer ${userStore.token}` } : {},
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300 && res.tempFilePath) {
          resolve(res.tempFilePath)
        } else {
          reject(new Error(`取素材失败(${res.statusCode})`))
        }
      },
      fail: reject,
    })
  })
}
