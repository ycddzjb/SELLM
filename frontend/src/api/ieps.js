import http from './http'

export const generateIep = (reportId) => http.post('/ieps', { reportId })
// 新链路:基于诊断生成 IEP(结构化五领域训练 + 合规约束)
export const generateIepFromDiagnosis = (diagnosisId) => http.post('/ieps', { diagnosisId })
export const getIep = (id) => http.get(`/ieps/${id}`)
export const finalizeIep = (id, content) => http.put(`/ieps/${id}/finalize`, { content })
export const listIepsByChild = (childId) => http.get('/ieps', { params: { childId } })
export const downloadIepPdf = (id) => http.get(`/ieps/${id}/pdf`, { responseType: 'blob' })
