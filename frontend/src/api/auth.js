import http from './http'

export const registerApi = (data) => http.post('/auth/register', data)
export const loginApi = (data) => http.post('/auth/login', data)
export const logoutApi = () => http.post('/auth/logout')
export const meApi = () => http.get('/auth/me')
