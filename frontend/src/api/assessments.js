import http from './http'

export const submitAssessment = (payload) => http.post('/assessments', payload)
export const listAssessmentsByChild = (childId) => http.get('/assessments', { params: { childId } })
