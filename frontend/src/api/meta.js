// 障碍类型枚举(与后端 com.sellm.common.DisorderType 对齐)
export const DISORDER_TYPES = [
  { code: 'ASD', label: '孤独症' },
  { code: 'DEVELOPMENTAL_DELAY', label: '发育迟缓' },
  { code: 'INTELLECTUAL', label: '智力障碍' },
  { code: 'LANGUAGE', label: '语言障碍' },
  { code: 'SENSORY_INTEGRATION', label: '感统失调' },
  { code: 'CEREBRAL_PALSY', label: '脑瘫' },
  { code: 'ADHD', label: '注意缺陷多动障碍' },
  { code: 'HEARING_VISION', label: '听视障' }
]

export const disorderLabel = (code) =>
  (DISORDER_TYPES.find((d) => d.code === code) || {}).label || code

export const disorderCsvToLabels = (csv) =>
  !csv ? '' : csv.split(',').map((c) => disorderLabel(c.trim())).join('、')
