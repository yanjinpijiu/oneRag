import http from './http'

export const pageUsersApi = (params) => http.get('/users', { params })
export const getUserApi = (userId) => http.get(`/users/${userId}`)
export const createUserApi = (data) => http.post('/users', data)
export const updateUserApi = (userId, data) => http.put(`/users/${userId}`, data)
export const deleteUserApi = (userId) => http.delete(`/users/${userId}`)
