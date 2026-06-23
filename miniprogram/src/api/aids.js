import { request } from '../utils/http'

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
