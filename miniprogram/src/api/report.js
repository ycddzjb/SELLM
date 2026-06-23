import { request } from '../utils/http'

/** 某孩子的报告列表(只读,家长不可生成)。 */
export function listReports(childId) {
  return request({ url: `/api/reports?childId=${childId}` })
}

/** 报告详情。 */
export function getReport(id) {
  return request({ url: `/api/reports/${id}` })
}
