import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const userInfo = ref(null)

  function setToken(t) { token.value = t }
  function setUser(u) { userInfo.value = u }
  function logout() { token.value = ''; userInfo.value = null }

  return { token, userInfo, setToken, setUser, logout }
})
