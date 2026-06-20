<template>
  <div v-loading="loading">
    <el-page-header @back="$router.push('/children')" :content="child ? child.name + ' 的档案' : '儿童档案'" />

    <el-descriptions v-if="child" :column="3" border style="margin:16px 0">
      <el-descriptions-item label="ID">{{ child.id }}</el-descriptions-item>
      <el-descriptions-item label="姓名">{{ child.name }}</el-descriptions-item>
      <el-descriptions-item label="障碍类型">{{ child.disorderType }}</el-descriptions-item>
      <el-descriptions-item label="复评时间">{{ child.reassessDate || '—' }}</el-descriptions-item>
      <el-descriptions-item label="IEP到期">{{ child.iepDueDate || '—' }}</el-descriptions-item>
      <el-descriptions-item label="干预进度">{{ child.interventionProgress || '—' }}</el-descriptions-item>
      <el-descriptions-item label="基线评估" :span="3">{{ child.baselineSummary || '—' }}</el-descriptions-item>
      <el-descriptions-item label="年度IEP方案" :span="3">{{ child.annualIepSummary || '—' }}</el-descriptions-item>
      <el-descriptions-item label="月度干预目标" :span="3">{{ child.monthlyGoal || '—' }}</el-descriptions-item>
    </el-descriptions>

    <div style="margin-bottom:12px">
      <el-button v-if="canEdit" type="primary" @click="openEditFields">编辑档案字段</el-button>
      <el-button type="primary" @click="goAssessment">新建评估</el-button>
    </div>

    <!-- 成长记录 -->
    <el-divider>成长记录</el-divider>
    <el-tabs v-model="activeLogType">
      <el-tab-pane v-for="t in LOG_TYPES" :key="t.code" :label="t.label" :name="t.code">
        <div v-if="canEdit" style="display:flex;gap:8px;margin-bottom:10px">
          <el-input v-model="newLogContent[t.code]" type="textarea" :rows="2"
                    :placeholder="`新增${t.label}记录`" style="flex:1" />
          <el-button type="primary" @click="onAddLog(t.code)">添加</el-button>
        </div>
        <el-table :data="logsByType(t.code)" border size="small">
          <el-table-column prop="content" label="内容" />
          <el-table-column prop="createdAt" label="时间" width="180" />
          <el-table-column prop="authorUserId" label="记录人ID" width="100" />
          <el-table-column v-if="canEdit" label="操作" width="90">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="onDeleteLog(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!logsByType(t.code).length" :description="`暂无${t.label}记录`" :image-size="50" />
      </el-tab-pane>
    </el-tabs>

    <el-divider>评估历史</el-divider>
    <el-table :data="assessments" border size="small">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="bandLabel" label="分段" />
      <el-table-column prop="totalScore" label="总分" width="90" />
      <el-table-column prop="interpretation" label="解读" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button size="small" @click="goReportFrom(row)">生成报告</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider>报告历史</el-divider>
    <el-table :data="reports" border size="small">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column prop="finalizedContent" label="定稿内容" show-overflow-tooltip />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" @click="openReport(row)">查看/定稿</el-button>
          <el-button size="small" @click="goIepFrom(row)">生成IEP</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider>IEP 历史</el-divider>
    <el-table :data="ieps" border size="small">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column prop="finalizedContent" label="定稿内容" show-overflow-tooltip />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button size="small" @click="openIep(row)">查看/定稿</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑档案字段 dialog -->
    <el-dialog v-model="editVisible" title="编辑档案字段" width="560px">
      <el-form label-width="100px">
        <el-form-item label="基线评估">
          <el-input v-model="editForm.baselineSummary" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="年度IEP方案">
          <el-input v-model="editForm.annualIepSummary" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="月度干预目标">
          <el-input v-model="editForm.monthlyGoal" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="复评时间">
          <el-date-picker v-model="editForm.reassessDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="IEP到期">
          <el-date-picker v-model="editForm.iepDueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="干预进度">
          <el-input v-model="editForm.interventionProgress" placeholder="如 进行中 / 已完成" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveFields">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getChild, updateChild, listChildLogs, createChildLog, deleteChildLog } from '../api/children'
import { listAssessmentsByChild } from '../api/assessments'
import { listReportsByChild } from '../api/reports'
import { listIepsByChild } from '../api/ieps'
import { LOG_TYPES } from '../api/meta'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const childId = Number(route.params.id)
const child = ref(null)
const assessments = ref([])
const reports = ref([])
const ieps = ref([])
const logs = ref([])
const loading = ref(false)

const activeLogType = ref('CLASSROOM_TRACK')
const newLogContent = reactive({})
// 老师/管理员可编辑字段与加记录;家长也可对自己孩子加记录(行级后端已校验)
const canEdit = computed(() => auth.isTeacher || auth.isManager || auth.isParent || auth.isSuperAdmin)

const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({
  baselineSummary: '', annualIepSummary: '', monthlyGoal: '',
  reassessDate: '', iepDueDate: '', interventionProgress: ''
})

function logsByType(type) {
  return logs.value.filter((l) => l.logType === type)
}

async function load() {
  loading.value = true
  try {
    child.value = await getChild(childId)
    assessments.value = await listAssessmentsByChild(childId)
    reports.value = await listReportsByChild(childId)
    ieps.value = await listIepsByChild(childId)
    logs.value = await listChildLogs(childId)
  } finally { loading.value = false }
}
onMounted(load)

function openEditFields() {
  editForm.baselineSummary = child.value.baselineSummary || ''
  editForm.annualIepSummary = child.value.annualIepSummary || ''
  editForm.monthlyGoal = child.value.monthlyGoal || ''
  editForm.reassessDate = child.value.reassessDate || ''
  editForm.iepDueDate = child.value.iepDueDate || ''
  editForm.interventionProgress = child.value.interventionProgress || ''
  editVisible.value = true
}

async function onSaveFields() {
  saving.value = true
  try {
    // 带上姓名/障碍类型(后端 update 以请求为准),其余归属字段后端保留
    await updateChild(childId, {
      name: child.value.name,
      disorderType: child.value.disorderType,
      baselineSummary: editForm.baselineSummary,
      annualIepSummary: editForm.annualIepSummary,
      monthlyGoal: editForm.monthlyGoal,
      reassessDate: editForm.reassessDate || null,
      iepDueDate: editForm.iepDueDate || null,
      interventionProgress: editForm.interventionProgress
    })
    ElMessage.success('档案已更新')
    editVisible.value = false
    await load()
  } catch (e) {} finally { saving.value = false }
}

async function onAddLog(type) {
  const content = (newLogContent[type] || '').trim()
  if (!content) { ElMessage.warning('请填写记录内容'); return }
  try {
    await createChildLog(childId, { logType: type, content })
    ElMessage.success('记录已添加')
    newLogContent[type] = ''
    logs.value = await listChildLogs(childId)
  } catch (e) {}
}

async function onDeleteLog(row) {
  try {
    await ElMessageBox.confirm('确认删除该记录?', '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteChildLog(childId, row.id)
    ElMessage.success('已删除')
    logs.value = await listChildLogs(childId)
  } catch (e) {}
}

function goAssessment() {
  router.push({ path: '/assessment', query: { childId } })
}
function goReportFrom(a) {
  router.push({ path: '/report', query: { assessmentId: a.id } })
}
function openReport(r) {
  router.push({ path: '/report', query: { reportId: r.id } })
}
function goIepFrom(r) {
  router.push({ path: '/iep', query: { reportId: r.id } })
}
function openIep(i) {
  router.push({ path: '/iep', query: { iepId: i.id } })
}
</script>
