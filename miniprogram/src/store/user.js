import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_KEY = 'sellm_auth'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const role = ref('')
  const username = ref('')

  function setAuth({ token: t, role: r, username: u }) {
    token.value = t || ''
    role.value = r || ''
    username.value = u || ''
    uni.setStorageSync(STORAGE_KEY, { token: token.value, role: role.value, username: username.value })
  }

  function restore() {
    try {
      const saved = uni.getStorageSync(STORAGE_KEY)
      if (saved && saved.token) {
        token.value = saved.token
        role.value = saved.role || ''
        username.value = saved.username || ''
      }
    } catch (e) { /* ignore */ }
  }

  function logout() {
    token.value = ''
    role.value = ''
    username.value = ''
    uni.removeStorageSync(STORAGE_KEY)
  }

  return { token, role, username, setAuth, restore, logout }
})
