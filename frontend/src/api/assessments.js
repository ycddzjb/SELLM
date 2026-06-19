import http from './http'

export const submitAssessment = (payload) => http.post('/assessments', payload)
