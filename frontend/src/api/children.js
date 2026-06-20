import http from './http'

export const listChildren = () => http.get('/children')
export const getChild = (id) => http.get(`/children/${id}`)
export const createChild = (payload) => http.post('/children', payload)
export const updateChild = (id, payload) => http.put(`/children/${id}`, payload)
export const deleteChild = (id) => http.delete(`/children/${id}`)
export const listReminders = () => http.get('/children/reminders')

// 儿童成长记录
export const listChildLogs = (childId, type) =>
  http.get(`/children/${childId}/logs`, type ? { params: { type } } : {})
export const createChildLog = (childId, payload) => http.post(`/children/${childId}/logs`, payload)
export const deleteChildLog = (childId, logId) => http.delete(`/children/${childId}/logs/${logId}`)
