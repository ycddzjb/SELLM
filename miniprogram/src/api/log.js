import { request } from '../utils/http'

/** 某孩子的成长记录列表。 */
export function listLogs(childId) {
  return request({ url: `/api/children/${childId}/logs` })
}

/** 添加成长记录。 */
export function createLog(childId, logType, content) {
  return request({ url: `/api/children/${childId}/logs`, method: 'POST', data: { logType, content } })
}
