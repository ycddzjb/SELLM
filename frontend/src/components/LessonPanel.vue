<template>
  <div>
    <!-- 录入 + 两步生成 -->
    <el-card style="max-width:920px;margin-bottom:16px">
      <el-form label-width="96px">
        <el-form-item label="教学主题">
          <el-input v-model="form.title" placeholder="如:认识情绪——高兴与难过" />
        </el-form-item>
        <el-form-item label="特教类型">
          <el-select v-model="form.specialEduType" filterable clearable placeholder="选择特教类型" style="width:260px">
            <el-option v-for="t in SPECIAL_EDU_TYPES" :key="t.label" :label="t.label" :value="t.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="教学学段">
          <el-select v-model="form.stage" filterable allow-create clearable placeholder="可选可输入" style="width:220px">
            <el-option v-for="s in TEACHING_STAGES" :key="s.label" :label="s.label" :value="s.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="教学学科">
          <el-select v-model="form.subject" filterable allow-create clearable placeholder="可选可输入" style="width:220px">
            <el-option v-for="s in TEACHING_SUBJECTS" :key="s.code" :label="s.label" :value="s.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程安排">
          <el-input v-model="form.schedule" placeholder="20分钟" style="width:220px" />
        </el-form-item>
        <el-form-item label="教学领域">
          <el-select v-model="form.field" filterable allow-create clearable placeholder="可选可输入" style="width:220px">
            <el-option v-for="f in TEACHING_FIELDS" :key="f.code" :label="f.label" :value="f.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="教学场景">
          <el-select v-model="form.scene" clearable placeholder="选择" style="width:220px">
            <el-option v-for="s in TEACHING_SCENES" :key="s.label" :label="s.label" :value="s.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级人数">
          <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:center">
            <span>总人数 <el-input-number v-model="form.total" :min="0" :max="100" size="small" controls-position="right" style="width:96px" /></span>
            <span>重度 <el-input-number v-model="form.severe" :min="0" :max="100" size="small" controls-position="right" style="width:90px" /></span>
            <span>中度 <el-input-number v-model="form.moderate" :min="0" :max="100" size="small" controls-position="right" style="width:90px" /></span>
            <span>轻度 <el-input-number v-model="form.mild" :min="0" :max="100" size="small" controls-position="right" style="width:90px" /></span>
            <span>正常 <el-input-number v-model="form.normal" :min="0" :max="100" size="small" controls-position="right" style="width:90px" /></span>
          </div>
        </el-form-item>
        <el-button type="primary" :loading="promptLoading" @click="onGenPrompt">生成提示词</el-button>
      </el-form>

      <!-- 提示词(可编辑) -->
      <template v-if="promptText !== null">
        <el-divider>提示词(可编辑)</el-divider>
        <el-input v-model="promptText" type="textarea" :rows="4" />
        <div style="margin-top:10px">
          <el-button type="primary" :loading="genLoading" @click="onGenContent">生成{{ typeLabel }}</el-button>
        </div>
      </template>

      <!-- 草稿(可编辑,不可导出/不落库) -->
      <template v-if="draft !== null">
        <el-divider>{{ typeLabel }}草稿(可编辑;定稿保存后方可导出/归档)</el-divider>
        <el-input v-model="draft" type="textarea" :rows="8" />
        <div style="margin-top:10px">
          <el-button type="success" :loading="finLoading" @click="onFinalize">定稿保存</el-button>
        </div>
      </template>
    </el-card>

    <!-- 已归档列表(FINALIZED) -->
    <el-table :data="list" size="small" style="max-width:920px;margin-bottom:16px">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" />
      <el-table-column label="操作" width="320">
        <template #default="{ row }">
          <el-button link type="primary" @click="openItem(row)">查看</el-button>
          <el-button link type="success" @click="exportItemWord(row)">导出 Word</el-button>
          <el-button v-if="type === 'LESSON'" link type="warning" @click="openCourseware(row)">生成课件</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 查看已归档项 -->
    <el-card v-if="item" style="max-width:920px;margin-bottom:16px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>{{ item.title || typeLabel }} #{{ item.id }}</span>
        <el-tag type="success">已归档</el-tag>
      </div>
      <pre class="box">{{ item.content }}</pre>
      <el-button type="success" plain @click="exportItemWord(item)">导出 Word</el-button>
    </el-card>

    <!-- 课件:基于选定教案生成(仅 LESSON) -->
    <el-card v-if="cwLesson" style="max-width:920px">
      <div style="font-weight:600;margin-bottom:8px">基于教案《{{ cwLesson.title }}》生成课件</div>
      <el-button type="primary" :loading="cwGenLoading" @click="onGenCourseware">生成课件草稿</el-button>
      <template v-if="cwDraft !== null">
        <el-divider>课件草稿(可编辑;定稿后方可导出 PPT)</el-divider>
        <el-input v-model="cwDraft" type="textarea" :rows="8" />
        <div style="margin-top:10px">
          <el-button type="success" :loading="cwFinLoading" @click="onFinalizeCourseware">课件定稿保存</el-button>
        </div>
      </template>
    </el-card>
  </div>
</template>
<!-- SCRIPT_PLACEHOLDER -->

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { TEACHING_FIELDS, TEACHING_SUBJECTS, TEACHING_STAGES, TEACHING_SCENES, SPECIAL_EDU_TYPES } from '../api/teachingMeta'
import { draftPrompt, draftContentGen, draftCourseware, finalizeNew, listContents } from '../api/teaching'
import { exportWord } from '../utils/exporter'

const props = defineProps({ type: { type: String, required: true } })  // PLAN | LESSON
const TYPE_LABELS = { PLAN: '训练方案', LESSON: '教案' }
const typeLabel = computed(() => TYPE_LABELS[props.type] || '内容')

const form = reactive({
  title: '', specialEduType: '', stage: '', subject: '', schedule: '', field: '', scene: '',
  total: null, severe: null, moderate: null, mild: null, normal: null
})
const promptText = ref(null)   // 提示词草稿(可编辑)
const draft = ref(null)        // 正文草稿(可编辑,不落库)
const list = ref([])
const item = ref(null)
const promptLoading = ref(false), genLoading = ref(false), finLoading = ref(false)

// 课件(仅 LESSON)
const cwLesson = ref(null)
const cwDraft = ref(null)
const cwGenLoading = ref(false), cwFinLoading = ref(false)

onMounted(loadList)

async function loadList() {
  try { list.value = (await listContents(props.type)).filter(c => c.status === 'FINALIZED') } catch (e) {}
}

function classComposition() {
  const n = (v) => (v == null ? 0 : v)
  return `总人数${n(form.total)}人(重度${n(form.severe)}/中度${n(form.moderate)}/轻度${n(form.mild)}/正常${n(form.normal)})`
}

function buildOptions() {
  return {
    specialEduType: form.specialEduType, stage: form.stage, subject: form.subject,
    schedule: form.schedule || '20分钟', field: form.field, scene: form.scene,
    classComposition: classComposition()
  }
}

// 据背景信息汇总成一段要求文本(供生成提示词)
function backgroundText() {
  const parts = []
  if (form.title) parts.push(`教学主题:${form.title}`)
  if (form.specialEduType) parts.push(`特教类型:${form.specialEduType}`)
  if (form.stage) parts.push(`教学学段:${form.stage}`)
  if (form.subject) parts.push(`教学学科:${form.subject}`)
  parts.push(`课程安排:${form.schedule || '20分钟'}`)
  if (form.field) parts.push(`教学领域:${form.field}`)
  if (form.scene) parts.push(`教学场景:${form.scene}`)
  parts.push(`班级构成:${classComposition()}`)
  return parts.join(';')
}

async function onGenPrompt() {
  if (!form.title.trim()) { ElMessage.warning('请填写教学主题'); return }
  promptLoading.value = true
  try {
    const r = await draftPrompt({
      contentType: props.type, title: form.title, requirement: backgroundText(),
      options: buildOptions(), subjectNames: []
    })
    promptText.value = r.content || ''
    draft.value = null
    ElMessage.success('已生成提示词,可编辑后生成' + typeLabel.value)
  } catch (e) {} finally { promptLoading.value = false }
}

async function onGenContent() {
  if (!promptText.value || !promptText.value.trim()) { ElMessage.warning('提示词不能为空'); return }
  genLoading.value = true
  try {
    const r = await draftContentGen({
      contentType: props.type, requirement: promptText.value,
      options: buildOptions(), subjectNames: []
    })
    draft.value = r.content || ''
    ElMessage.success('已生成草稿,编辑后可定稿保存')
  } catch (e) {} finally { genLoading.value = false }
}

async function onFinalize() {
  if (!draft.value || !draft.value.trim()) { ElMessage.warning('草稿不能为空'); return }
  finLoading.value = true
  try {
    await finalizeNew({
      contentType: props.type, title: form.title, options: buildOptions(), content: draft.value
    })
    ElMessage.success('已定稿归档')
    promptText.value = null; draft.value = null
    loadList()
  } catch (e) {} finally { finLoading.value = false }
}

function openItem(row) { item.value = row }
function exportItemWord(row) {
  try { exportWord(row.title || typeLabel.value, row.content) } catch (e) { ElMessage.error('导出失败') }
}

// ── 课件:基于选定已定稿教案 ──
function openCourseware(lesson) {
  cwLesson.value = lesson
  cwDraft.value = null
}
async function onGenCourseware() {
  cwGenLoading.value = true
  try {
    const r = await draftCourseware({ lessonId: cwLesson.value.id, subjectNames: [] })
    cwDraft.value = r.content || ''
    ElMessage.success('已生成课件草稿,编辑后可定稿')
  } catch (e) {} finally { cwGenLoading.value = false }
}
async function onFinalizeCourseware() {
  if (!cwDraft.value || !cwDraft.value.trim()) { ElMessage.warning('课件草稿不能为空'); return }
  cwFinLoading.value = true
  try {
    await finalizeNew({
      contentType: 'COURSEWARE', title: cwLesson.value.title + ' · 课件',
      options: {}, content: cwDraft.value, sourceId: cwLesson.value.id
    })
    ElMessage.success('课件已定稿归档')
    cwDraft.value = null; cwLesson.value = null
  } catch (e) {} finally { cwFinLoading.value = false }
}
</script>

<style scoped>
.box { white-space:pre-wrap; background:#f7f7f7; padding:12px; border-radius:4px; max-height:320px; overflow:auto; }
</style>
