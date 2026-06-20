import http from './http'

export const generateReport = (assessmentId) => http.post('/reports', { assessmentId })
export const getReport = (id) => http.get(`/reports/${id}`)
export const finalizeReport = (id, content) => http.put(`/reports/${id}/finalize`, { content })
export const listReportsByChild = (childId) => http.get('/reports', { params: { childId } })
export const downloadReportPdf = (id) => http.get(`/reports/${id}/pdf`, { responseType: 'blob' })
