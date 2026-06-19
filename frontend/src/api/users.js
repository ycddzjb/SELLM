import http from './http'

export const createUser = (payload) => http.post('/users', payload)
