import { defineStore } from 'pinia'

const ROLE_LABELS = {
  SUPER_ADMIN: '超级管理者',
  MANAGER: '管理者',
  TEACHER: '老师/康复师',
  PARENT: '家长'
}

// 超级管理者为平台级,代表学院本身
const SUPER_ADMIN_ORG = '南京特殊教育师范学院'

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
    roleLabel: (s) => ROLE_LABELS[s.role] || s.role,
    // 顶栏机构名:超管显示学院名;其余显示真实机构名(无则空)
    orgLabel: (s) => {
      if (s.role === 'SUPER_ADMIN') return SUPER_ADMIN_ORG
      return s.orgName && s.orgName !== '未知机构' ? s.orgName : ''
    }
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
