import { defineStore } from 'pinia'

const ROLE_LABELS = {
  SUPER_ADMIN: '超级管理者',
  MANAGER: '管理者',
  TEACHER: '老师/康复师',
  PARENT: '家长'
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    role: localStorage.getItem('role') || '',
    username: localStorage.getItem('username') || '',
    orgName: localStorage.getItem('orgName') || ''
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    isManager: (s) => s.role === 'MANAGER',
    isSuperAdmin: (s) => s.role === 'SUPER_ADMIN',
    roleLabel: (s) => ROLE_LABELS[s.role] || s.role
  },
  actions: {
    setAuth({ token, role, username, orgName }) {
      this.token = token
      this.role = role
      this.username = username || ''
      this.orgName = orgName || ''
      localStorage.setItem('token', token)
      localStorage.setItem('role', role)
      localStorage.setItem('username', this.username)
      localStorage.setItem('orgName', this.orgName)
    },
    logout() {
      this.token = ''
      this.role = ''
      this.username = ''
      this.orgName = ''
      localStorage.removeItem('token')
      localStorage.removeItem('role')
      localStorage.removeItem('username')
      localStorage.removeItem('orgName')
    }
  }
})
