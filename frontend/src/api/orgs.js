import http from './http'

export const publicOrgs = () => http.get('/orgs/public')
export const publicOrgClasses = (orgId) => http.get(`/orgs/public/${orgId}/classes`)
export const listOrgs = () => http.get('/orgs')
export const createOrg = (payload) => http.post('/orgs', payload)
export const updateOrg = (id, payload) => http.put(`/orgs/${id}`, payload)
export const deleteOrg = (id) => http.delete(`/orgs/${id}`)
export const batchCreateOrgs = (list) => http.post('/orgs/batch', list)
