import http from './http'

export const listScales = (disorderType) =>
  http.get('/scales', disorderType ? { params: { disorderType } } : {})
export const getScale = (scaleId) => http.get(`/scales/${scaleId}`)
export const createScale = (payload) => http.post('/scales', payload)
export const updateScale = (scaleId, payload) => http.put(`/scales/${scaleId}`, payload)
export const deleteScale = (scaleId) => http.delete(`/scales/${scaleId}`)
