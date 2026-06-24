import http from './http'

export const ask = (payload) => http.post('/qa/ask', payload)  // {question, conversationId?, subjectNames?}
export const listConversations = () => http.get('/qa/conversations')
export const getConversation = (id) => http.get(`/qa/conversations/${id}`)

// 多模态文档/图片分析(匿名可用)。file + 可选 question → 分析文本
export const analyzeDoc = (file, question) => {
  const fd = new FormData()
  fd.append('file', file)
  if (question) fd.append('question', question)
  return http.post('/qa/analyze', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}
