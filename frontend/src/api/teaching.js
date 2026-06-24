import http from './http'

// ── 教案(lesson plan)──
// generate 需 iepContent(定稿 IEP 正文)+ scene/mode/disorderType;subjectNames 供出网脱敏屏蔽表
export const generatePlan = (payload) => http.post('/teaching/lesson-plans', payload)
export const editPlan = (id, content) => http.put(`/teaching/lesson-plans/${id}`, { content })
export const finalizePlan = (id) => http.post(`/teaching/lesson-plans/${id}/finalize`)
export const getPlan = (id) => http.get(`/teaching/lesson-plans/${id}`)
export const listPlans = () => http.get('/teaching/lesson-plans')

// ── 课件(courseware,须基于已定稿教案)──
export const generateCourseware = (payload) => http.post('/teaching/courseware', payload)
export const editCourseware = (id, content) => http.put(`/teaching/courseware/${id}`, { content })
export const finalizeCourseware = (id) => http.post(`/teaching/courseware/${id}/finalize`)
export const getCourseware = (id) => http.get(`/teaching/courseware/${id}`)
