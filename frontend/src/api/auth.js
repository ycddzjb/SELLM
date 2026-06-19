import http from './http'

export function login(username, password) {
  return http.post('/auth/login', { username, password })
}
