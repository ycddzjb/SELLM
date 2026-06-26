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

// ── 教学模块统一内容(训练方案/教案/课件/习题)──
export const generateContent = (payload) => http.post('/teaching/contents', payload)  // {contentType,title,requirement,options,subjectNames}
export const editContent = (id, content) => http.put(`/teaching/contents/${id}`, { content })
export const finalizeContent = (id) => http.post(`/teaching/contents/${id}/finalize`)
export const listContents = (type) => http.get('/teaching/contents', { params: { type } })

// ── 两步生成(草稿不落库):提示词 → 正文;课件基于定稿教案 ──
export const draftPrompt = (payload) => http.post('/teaching/contents/draft/prompt', payload)        // {contentType,title,requirement,options,subjectNames} → {content}
export const draftContentGen = (payload) => http.post('/teaching/contents/draft/content', payload)   // {contentType,requirement(提示词),options,subjectNames} → {content}
export const draftCourseware = (payload) => http.post('/teaching/contents/draft/courseware', payload) // {lessonId,subjectNames} → {content}
// 定稿落库(草稿态此前不入库):{contentType,title,options,content,sourceId?}
export const finalizeNew = (payload) => http.post('/teaching/contents/finalize-new', payload)
