import http from './http'

export const generateIep = (reportId) => http.post('/ieps', { reportId })
export const getIep = (id) => http.get(`/ieps/${id}`)
export const finalizeIep = (id, content) => http.put(`/ieps/${id}/finalize`, { content })
export const listIepsByChild = (childId) => http.get('/ieps', { params: { childId } })
export const downloadIepPdf = (id) => http.get(`/ieps/${id}/pdf`, { responseType: 'blob' })
