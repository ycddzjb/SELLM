<template>
  <div>
    <el-card style="max-width:900px;margin-bottom:16px">
      <el-form label-width="96px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="如:认识情绪——高兴与难过" />
        </el-form-item>
        <el-form-item label="残障类型">
          <el-select v-model="form.disorderType" placeholder="选择" style="width:220px">
            <el-option v-for="d in DISORDER_TYPES" :key="d.code" :label="d.label" :value="d.code" />
          </el-select>
        </el-form-item>

        <!-- 教案/课件:教学领域 + 教学形式 -->
        <template v-if="type === 'LESSON' || type === 'COURSEWARE'">
          <el-form-item label="教学领域">
            <el-select v-model="form.field" placeholder="选择" style="width:220px">
              <el-option v-for="f in TEACHING_FIELDS" :key="f.code" :label="f.label" :value="f.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="教学形式">
            <el-select v-model="form.form" placeholder="选择" style="width:220px">
              <el-option v-for="f in TEACHING_FORMS" :key="f.code" :label="f.label" :value="f.code" />
            </el-select>
          </el-form-item>
        </template>

        <!-- 案例:教学学科 -->
        <el-form-item v-if="type === 'CASE'" label="教学学科">
          <el-select v-model="form.subject" placeholder="选择" style="width:220px">
            <el-option v-for="s in TEACHING_SUBJECTS" :key="s.code" :label="s.label" :value="s.code" />
          </el-select>
        </el-form-item>

        <!-- 习题:题型/难度/学段/方向 -->
        <template v-if="type === 'EXERCISE'">
          <el-form-item label="题型">
            <el-select v-model="form.questionType" style="width:220px">
              <el-option v-for="q in QUESTION_TYPES" :key="q.code" :label="q.label" :value="q.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="form.difficulty" style="width:220px">
              <el-option v-for="d in DIFFICULTIES" :key="d.code" :label="d.label" :value="d.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="学段">
            <el-select v-model="form.stage" style="width:220px">
              <el-option v-for="s in STAGES" :key="s.code" :label="s.label" :value="s.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="出题方向">
            <el-select v-model="form.direction" style="width:220px">
              <el-option v-for="d in QUESTION_DIRECTIONS" :key="d.code" :label="d.label" :value="d.code" />
            </el-select>
          </el-form-item>
        </template>

        <el-form-item label="内容与要求">
          <el-input v-model="form.requirement" type="textarea" :rows="4" placeholder="输入标题/文本/章节/大纲/知识点等内容与具体要求" />
        </el-form-item>
        <el-button type="primary" :loading="genLoading" @click="onGenerate">生成{{ typeLabel }}</el-button>
        <el-button @click="loadList">刷新列表</el-button>
      </el-form>
    </el-card>

    <el-table :data="list" size="small" style="max-width:900px;margin-bottom:16px">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><el-tag :type="row.status==='FINALIZED'?'success':'info'">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }"><el-button link type="primary" @click="openItem(row)">查看/编辑</el-button></template>
      </el-table-column>
    </el-table>

    <el-card v-if="item" style="max-width:900px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>{{ item.title || typeLabel }} #{{ item.id }}</span>
        <el-tag :type="item.status==='FINALIZED'?'success':'info'">{{ item.status }}</el-tag>
      </div>
      <el-divider>AI 草案</el-divider>
      <pre style="white-space:pre-wrap;background:#f7f7f7;padding:12px;border-radius:4px;max-height:300px;overflow:auto">{{ item.aiDraft }}</pre>
      <el-divider>定稿内容</el-divider>
      <el-input v-model="editContent" type="textarea" :rows="8" :disabled="item.status==='FINALIZED'" />
      <div style="margin-top:12px">
        <el-button v-if="item.status!=='FINALIZED'" :loading="editLoading" @click="onEdit">保存</el-button>
        <el-button v-if="item.status!=='FINALIZED'" type="primary" :loading="finLoading" @click="onFinalize">定稿</el-button>
        <el-button type="success" plain @click="onExportWord">导出 Word</el-button>
        <el-button type="warning" plain @click="onExportPpt">一键 PPT</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { DISORDER_TYPES } from '../api/meta'
import { TEACHING_FIELDS, TEACHING_FORMS, TEACHING_SUBJECTS, QUESTION_TYPES, DIFFICULTIES, STAGES, QUESTION_DIRECTIONS } from '../api/teachingMeta'
import { generateContent, editContent as apiEdit, finalizeContent, listContents } from '../api/teaching'
import { exportWord, exportPpt } from '../utils/exporter'

const props = defineProps({ type: { type: String, required: true } })
const TYPE_LABELS = { LESSON: '教案', COURSEWARE: '课件', CASE: '案例', EXERCISE: '习题' }
const typeLabel = computed(() => TYPE_LABELS[props.type] || '内容')

const form = reactive({ title: '', disorderType: 'ASD', field: '', form: '', subject: '', questionType: '', difficulty: '', stage: '', direction: '', requirement: '' })
const list = ref([])
const item = ref(null)
const editContentVal = ref('')
const editContent = editContentVal   // 模板用 editContent
const genLoading = ref(false), editLoading = ref(false), finLoading = ref(false)

onMounted(loadList)

async function loadList() {
  try { list.value = await listContents(props.type) } catch (e) {}
}

function buildOptions() {
  const o = { disorderType: form.disorderType }
  if (props.type === 'LESSON' || props.type === 'COURSEWARE') { o.field = labelOf(TEACHING_FIELDS, form.field); o.form = labelOf(TEACHING_FORMS, form.form) }
  if (props.type === 'CASE') o.subject = labelOf(TEACHING_SUBJECTS, form.subject)
  if (props.type === 'EXERCISE') {
    o.questionType = labelOf(QUESTION_TYPES, form.questionType)
    o.difficulty = labelOf(DIFFICULTIES, form.difficulty)
    o.stage = labelOf(STAGES, form.stage)
    o.direction = labelOf(QUESTION_DIRECTIONS, form.direction)
  }
  return o
}
function labelOf(arr, code) { const x = arr.find(i => i.code === code); return x ? x.label : '' }

async function onGenerate() {
  if (!form.requirement.trim()) { ElMessage.warning('请输入内容与要求'); return }
  genLoading.value = true
  try {
    item.value = await generateContent({
      contentType: props.type, title: form.title, requirement: form.requirement,
      options: buildOptions(), subjectNames: []
    })
    editContentVal.value = item.value.content || item.value.aiDraft
    ElMessage.success('已生成草案'); loadList()
  } catch (e) {} finally { genLoading.value = false }
}
function openItem(row) {
  // 列表项已含 content/aiDraft,直接展开编辑
  item.value = row
  editContentVal.value = row.content || row.aiDraft
}
async function onEdit() {
  editLoading.value = true
  try { item.value = await apiEdit(item.value.id, editContentVal.value); ElMessage.success('已保存') } catch (e) {} finally { editLoading.value = false }
}
async function onFinalize() {
  finLoading.value = true
  try { item.value = await finalizeContent(item.value.id); ElMessage.success('已定稿'); loadList() } catch (e) {} finally { finLoading.value = false }
}
async function onExportWord() {
  try { await exportWord(item.value.title || typeLabel.value, editContentVal.value || item.value.content) } catch (e) { ElMessage.error('导出失败') }
}
async function onExportPpt() {
  try { await exportPpt(item.value.title || typeLabel.value, editContentVal.value || item.value.content) } catch (e) { ElMessage.error('导出失败') }
}
</script>
