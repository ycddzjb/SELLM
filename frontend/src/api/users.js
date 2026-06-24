import http from './http'

export const listUsers = () => http.get('/users')
export const createUser = (payload) => http.post('/users', payload)
export const listPendingParents = () => http.get('/users/pending')
export const listParents = () => http.get('/users/parents')
export const approveUser = (id) => http.put(`/users/${id}/approve`)
export const rejectUser = (id) => http.put(`/users/${id}/reject`)
export const listPendingWeChat = () => http.get('/users/pending-wechat')
export const activateWeChat = (id, payload) => http.put(`/users/${id}/activate-wechat`, payload)
export const rejectWeChat = (id) => http.put(`/users/${id}/reject-wechat`)
export const changeMyPassword = (oldPassword, newPassword) =>
  http.put('/users/me/password', { oldPassword, newPassword })
// 超管:编辑(状态/角色/机构)+ 软删用户
export const updateUser = (id, payload) => http.put(`/users/${id}`, payload)
export const deleteUser = (id) => http.delete(`/users/${id}`)
