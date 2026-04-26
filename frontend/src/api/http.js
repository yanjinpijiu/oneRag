import axios from 'axios'
import { useAuthStore } from '../stores/auth'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000
})

http.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = authStore.token
  }
  return config
})

http.interceptors.response.use(
  (res) => res.data,
  (err) => Promise.reject(err)
)

export default http
