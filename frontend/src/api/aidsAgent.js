import http from './http'

// 教具推荐(disorderType 可空 → 全部)
export const recommendAids = (disorderType) =>
  http.get('/aids/recommendations', { params: disorderType ? { disorderType } : {} })

// 文生素材:提交(202 → {taskId})、轮询、列表
export const submitAsset = (payload) => http.post('/aids/assets', payload)  // {type, prompt, subjectNames}
export const pollAssetTask = (taskId) => http.get(`/aids/tasks/${taskId}`)
export const listAssets = () => http.get('/aids/assets')

// 受 JWT 保护的产物原始字节 → 经 axios 带 token 取 blob 转 URL(image 标签不带鉴权头)
export const fetchAssetBlobUrl = async (id) => {
  const blob = await http.get(`/aids/assets/${id}/raw`, { responseType: 'blob' })
  return URL.createObjectURL(blob)
}
