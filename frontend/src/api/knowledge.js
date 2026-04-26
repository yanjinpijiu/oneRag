import http from './http'

export const pageKbDocumentsApi = (params) => http.get('/kb/documents', { params })
export const getKbDocumentApi = (documentId) => http.get(`/kb/documents/${documentId}`)
export const processKbDocumentApi = (documentId, data) => http.post(`/kb/documents/${documentId}/process`, data || {})
export const deleteKbDocumentApi = (documentId) => http.delete(`/kb/documents/${documentId}`)
export const getKbDownloadUrlApi = (documentId) => http.get(`/kb/documents/${documentId}/download-url`)
