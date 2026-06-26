// 教学模块选项常量(教案/课件/案例/习题)。
export const TEACHING_FIELDS = [
  { code: 'MOTOR', label: '运动能力' },
  { code: 'LANGUAGE', label: '语言沟通' },
  { code: 'COGNITION', label: '认知理解' },
  { code: 'SOCIAL', label: '社会交往' },
  { code: 'SELF_CARE', label: '生活自理' },
  { code: 'BEHAVIOR', label: '行为管理' }
]

export const TEACHING_FORMS = [
  { code: 'ONE_ON_ONE', label: '一对一教学' },
  { code: 'GROUP', label: '集体课堂教学' }
]

// 案例:教学学科
export const TEACHING_SUBJECTS = [
  { code: 'LIFE_CHINESE', label: '生活语文' },
  { code: 'LIFE_MATH', label: '生活数学' },
  { code: 'LIFE_ADAPT', label: '生活适应' },
  { code: 'SOCIAL_COMM', label: '社交沟通' },
  { code: 'FINE_MOTOR', label: '精细动作' }
]

// 习题:题型/难度/学段/方向
export const QUESTION_TYPES = [
  { code: 'CHOICE', label: '选择题' },
  { code: 'BLANK', label: '填空题' },
  { code: 'QA', label: '问答题' },
  { code: 'PICTURE', label: '看图题' },
  { code: 'MATCH', label: '连线题' }
]
export const DIFFICULTIES = [
  { code: 'EASY', label: '易' },
  { code: 'MEDIUM', label: '中' },
  { code: 'HARD', label: '难' }
]
export const STAGES = [
  { code: 'PRESCHOOL', label: '学前' },
  { code: 'PRIMARY', label: '小学' },
  { code: 'JUNIOR', label: '初中' }
]
export const QUESTION_DIRECTIONS = [
  { code: 'VOCAB', label: '词汇拓展' },
  { code: 'COMPREHENSION', label: '语言理解' },
  { code: 'NARRATIVE', label: '叙事表达' },
  { code: 'LOGIC', label: '逻辑推理' },
  { code: 'LIFE_SKILL', label: '生活技能' }
]

// 三子功能(训练方案/教案/课件)用:学段细化(学前 + 小学1-6 + 初中7-9);label 直接作选项值
export const TEACHING_STAGES = [
  { label: '学前' },
  { label: '小学一年级' }, { label: '小学二年级' }, { label: '小学三年级' },
  { label: '小学四年级' }, { label: '小学五年级' }, { label: '小学六年级' },
  { label: '初中七年级' }, { label: '初中八年级' }, { label: '初中九年级' }
]

// 教学场景
export const TEACHING_SCENES = [
  { label: '家庭' }, { label: '机构' }, { label: '学校' }
]
