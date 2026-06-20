import http from './http'

// 上传评估素材(multipart):file 可空(纯笔记),noteText/scaleId/mediaType
export const uploadMedia = (childId, formData) =>
  http.post(`/children/${childId}/evaluation-media`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })

// 触发多模态识别,返回各指标建议 [{itemId, suggestedScore, reason}]
export const analyzeMedia = (childId, mediaId) =>
  http.post(`/children/${childId}/evaluation-media/${mediaId}/analyze`)

export const listMedia = (childId, type) =>
  http.get(`/children/${childId}/evaluation-media`, type ? { params: { type } } : {})
