import http from './http'

export const publicOrgs = () => http.get('/orgs/public')
export const listOrgs = () => http.get('/orgs')
export const createOrg = (payload) => http.post('/orgs', payload)
