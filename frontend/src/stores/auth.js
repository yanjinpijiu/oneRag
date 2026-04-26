import { defineStore } from 'pinia'
import { meApi } from '../api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: null,
    loaded: false
  }),
  actions: {
    setToken(token) {
      this.token = token || ''
      localStorage.setItem('token', this.token)
    },
    clear() {
      this.token = ''
      this.user = null
      this.loaded = false
      localStorage.removeItem('token')
    },
    setUser(user) {
      this.user = user
      this.loaded = true
    },
    async ensureUser() {
      if (!this.token) return false
      if (this.loaded && this.user) return true
      try {
        const res = await meApi()
        if (res.code !== '0') {
          this.clear()
          return false
        }
        this.user = res.data || null
        this.loaded = true
        return true
      } catch (e) {
        this.clear()
        return false
      }
    }
  }
})
