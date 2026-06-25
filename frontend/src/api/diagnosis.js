import http from './http'

// 建诊断(DRAFT 空内容),返回 id 供挂素材;入参 {childId, scaleId?, structuredInput?}
export const createDiagnosis = (payload) => http.post('/diagnoses', payload)

// 挂多模态素材并识别(multipart):mediaType=TEXT/IMAGE/VIDEO/AUDIO;file 可空(纯文本用 noteText)
export const addDiagnosisMedia = (id, formData) =>
  http.post(`/diagnoses/${id}/media`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })

// 生成结构化诊断(聚合已挂素材 + 结构化输入 + 量表知识库)
export const generateDiagnosis = (id, subjectNames) =>
  http.post(`/diagnoses/${id}/generate`, { subjectNames: subjectNames || [] })

export const editDiagnosis = (id, draft) => http.put(`/diagnoses/${id}`, { draft })
export const finalizeDiagnosis = (id, content) => http.post(`/diagnoses/${id}/finalize`, { content })
export const getDiagnosis = (id) => http.get(`/diagnoses/${id}`)
export const listDiagnosesByChild = (childId) => http.get('/diagnoses', { params: { childId } })
export const downloadDiagnosisPdf = (id) => http.get(`/diagnoses/${id}/pdf`, { responseType: 'blob' })
