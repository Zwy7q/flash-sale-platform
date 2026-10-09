import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { currentUserRequest, loginRequest } from '../api/auth.js'
import { clearToken, readToken, saveToken } from '../utils/session.js'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(readToken())
  const user = ref(null)
  const initialized = ref(false)
  const isAuthenticated = computed(() => Boolean(token.value && user.value))
  const isAdmin = computed(() => Boolean(user.value && user.value.isAdmin === true))
  let restoration = null
  let sessionVersion = 0

  function logout() {
    // 版本变化意味着之前尚未完成的恢复请求已经作废。
    sessionVersion += 1
    clearToken()
    token.value = ''
    user.value = null
    initialized.value = true
    restoration = null
  }

  async function login(credentials) {
    const version = ++sessionVersion
    const response = await loginRequest(credentials)
    if (version !== sessionVersion) {
      return false
    }
    saveToken(response.data.token)
    token.value = response.data.token
    user.value = response.data.user
    initialized.value = true
    return true
  }

  async function restoreSession() {
    if (initialized.value) {
      return
    }
    if (!token.value) {
      initialized.value = true
      return
    }
    // 多个路由同时需要会话时共用一个请求，不重复调用 /me。
    if (restoration) {
      return restoration
    }
    const version = sessionVersion
    restoration = restoreUser(version)
    try {
      await restoration
    } finally {
      if (version === sessionVersion) {
        restoration = null
      }
    }
  }

  async function restoreUser(version) {
    try {
      const response = await currentUserRequest()
      if (version === sessionVersion) {
        user.value = response.data
        initialized.value = true
      }
    } catch (error) {
      if (version !== sessionVersion) {
        return
      }
      if (error.response && (error.response.status === 401 || error.response.status === 404)) {
        logout()
        return
      }
      // 断网、503 等不证明 Token 无效。保留 Token，页面可重试恢复。
      throw error
    }
  }

  return { token, user, initialized, isAuthenticated, isAdmin, login, logout, restoreSession }
})
