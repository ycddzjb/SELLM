import http from './http'

// ── 课题申报书 ──
export const generateProposal = (payload) => http.post('/research/proposals', payload)  // {topic, subjectNames}
export const editProposal = (id, content) => http.put(`/research/proposals/${id}`, { content })
export const finalizeProposal = (id) => http.post(`/research/proposals/${id}/finalize`)
export const getProposal = (id) => http.get(`/research/proposals/${id}`)
export const listProposals = () => http.get('/research/proposals')

// ── 信效度计算 ──
export const computeReliability = (payload) => http.post('/research/reliability', payload)  // {method, scores:[[..],..]}
export const getReliability = (id) => http.get(`/research/reliability/${id}`)
export const listReliability = () => http.get('/research/reliability')
