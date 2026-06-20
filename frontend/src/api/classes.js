import http from './http'

export const listClasses = (orgId) =>
  http.get('/classes', orgId ? { params: { orgId } } : {})
export const createClass = (payload) => http.post('/classes', payload)
export const updateClass = (id, payload) => http.put(`/classes/${id}`, payload)
export const deleteClass = (id) => http.delete(`/classes/${id}`)
