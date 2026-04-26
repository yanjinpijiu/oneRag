import http from './http'

export const listConversationsApi = () => http.get('/chat/conversations')
export const listConversationMessagesApi = (conversationId) => http.get(`/chat/conversations/${conversationId}/messages`)
export const deleteConversationApi = (conversationId) => http.delete(`/chat/conversations/${conversationId}`)
