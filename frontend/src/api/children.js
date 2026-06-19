import http from './http'

export const listChildren = () => http.get('/children')
export const getChild = (id) => http.get(`/children/${id}`)
export const createChild = (payload) => http.post('/children', payload)
export const updateChild = (id, payload) => http.put(`/children/${id}`, payload)
export const deleteChild = (id) => http.delete(`/children/${id}`)
