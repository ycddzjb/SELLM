import { request } from '../utils/http'

/** 我可见的孩子列表(家长限自己孩子)。 */
export function listChildren() {
  return request({ url: '/api/children' })
}

/** 孩子详情。 */
export function getChild(id) {
  return request({ url: `/api/children/${id}` })
}

/** 复评/IEP 到期提醒(行级,只读)。 */
export function listReminders() {
  return request({ url: '/api/children/reminders' })
}

/** 推荐量表。 */
export function recommendedScales(id) {
  return request({ url: `/api/children/${id}/recommended-scales` })
}
