import http from './http'

export function login(username, password) {
  return http.post('/auth/login', { username, password })
}

export function register(payload) {
  return http.post('/auth/register', payload)
}
