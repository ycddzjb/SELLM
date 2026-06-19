import http from './http'

export const listUsers = () => http.get('/users')
export const createUser = (payload) => http.post('/users', payload)
export const listPendingParents = () => http.get('/users/pending')
export const approveUser = (id) => http.put(`/users/${id}/approve`)
export const rejectUser = (id) => http.put(`/users/${id}/reject`)
export const changeMyPassword = (oldPassword, newPassword) =>
  http.put('/users/me/password', { oldPassword, newPassword })
