/** 障碍类型 code → 中文 label(须与后端 com.sellm.common.DisorderType 对齐)。 */
const DISORDER_LABELS = {
  ASD: '孤独症',
  DEVELOPMENTAL_DELAY: '发育迟缓',
  INTELLECTUAL: '智力障碍',
  LANGUAGE: '语言障碍',
  SENSORY_INTEGRATION: '感统失调',
  CEREBRAL_PALSY: '脑瘫',
  ADHD: '注意缺陷多动障碍',
  HEARING_VISION: '听视障',
}

export function disorderLabel(code) {
  return DISORDER_LABELS[code] || code || '未指定'
}

export const DISORDER_OPTIONS = Object.keys(DISORDER_LABELS).map((k) => ({
  value: k,
  label: DISORDER_LABELS[k],
}))

/** 草案/任务状态 → 中文 label。 */
const STATUS_LABELS = {
  DRAFT: '草案',
  FINALIZED: '已定稿',
  PENDING: '排队中',
  RUNNING: '生成中',
  SUCCESS: '已完成',
  FAILED: '失败',
}

export function statusLabel(s) {
  return STATUS_LABELS[s] || s || ''
}

/** 素材类型选项。 */
export const ASSET_TYPE_OPTIONS = [
  { value: 'IMAGE', label: '教学插图' },
  { value: 'PICTUREBOOK', label: '社交故事绘本' },
  { value: 'AUDIO', label: '听觉训练音频' },
  { value: 'VIDEO', label: '教学短视频' },
]
