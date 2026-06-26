import http from './http'

// 训练周期
export const createCycle = (payload) => http.post('/training-cycles', payload)  // {childId,diagnosisId?,iepId?,title?}
export const getCycle = (id) => http.get(`/training-cycles/${id}`)
export const listCyclesByChild = (childId) => http.get('/training-cycles', { params: { childId } })
export const closeCycle = (id) => http.post(`/training-cycles/${id}/close`)

// 训练数据(multipart:mediaType + file? + noteText? + scores?(JSON))
export const addRecord = (cycleId, formData) =>
  http.post(`/training-cycles/${cycleId}/records`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
export const listRecords = (cycleId) => http.get(`/training-cycles/${cycleId}/records`)

// 阶段评估
export const generateStageEval = (cycleId) => http.post(`/training-cycles/${cycleId}/stage-eval`)
export const getStageEval = (evalId) => http.get(`/training-cycles/stage-evals/${evalId}`)
export const editStageEval = (evalId, draft) => http.put(`/training-cycles/stage-evals/${evalId}`, { draft })
export const finalizeStageEval = (evalId, content) => http.post(`/training-cycles/stage-evals/${evalId}/finalize`, { content })

// 纵向对比(某 child 各周期阶段评估)
export const compareByChild = (childId) => http.get('/training-cycles/compare', { params: { childId } })

// 据阶段评估适配性生成新版 IEP
export const generateNextIep = (cycleId) => http.post(`/training-cycles/${cycleId}/next-iep`)
