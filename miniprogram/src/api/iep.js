import { request } from '../utils/http'

/** 某孩子的家庭 IEP 列表。 */
export function listFamilyIeps(childId) {
  return request({ url: `/api/family-ieps?childId=${childId}` })
}

/** 生成家庭 IEP 草案。 */
export function generateFamilyIep(childId, parentGoal) {
  return request({ url: '/api/family-ieps', method: 'POST', data: { childId, parentGoal } })
}

/** 家庭 IEP 详情。 */
export function getFamilyIep(id) {
  return request({ url: `/api/family-ieps/${id}` })
}
