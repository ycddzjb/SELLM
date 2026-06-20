import http from './http'

export const generateFamilyIep = (childId, parentGoal) =>
  http.post('/family-ieps', { childId, parentGoal })
export const getFamilyIep = (id) => http.get(`/family-ieps/${id}`)
export const listFamilyIepsByChild = (childId) => http.get('/family-ieps', { params: { childId } })
export const finalizeFamilyIep = (id, content) => http.put(`/family-ieps/${id}/finalize`, { content })
export const downloadFamilyIepPdf = (id) => http.get(`/family-ieps/${id}/pdf`, { responseType: 'blob' })

// blob → 触发浏览器下载
export function saveBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
}
