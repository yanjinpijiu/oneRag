import http from './http'

export const getProfileApi = () => http.get('/profile')
export const updateProfileApi = (data) => http.put('/profile', data)
export const changePasswordApi = (data) => http.put('/profile/password', data)
