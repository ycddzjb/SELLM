<template>
  <div>
    <el-card v-if="reminders.length" style="margin-bottom:16px">
      <template #header>
        <span>到期提醒(30 天内 / 已逾期)</span>
      </template>
      <el-table :data="reminders" border size="small" @row-click="goDetailById">
        <el-table-column prop="name" label="姓名" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ reminderTypeLabel(row.reminderType) }}</template>
        </el-table-column>
        <el-table-column prop="dueDate" label="到期日" width="140" />
        <el-table-column label="剩余" width="140">
          <template #default="{ row }">
            <el-tag size="small" :type="row.overdue ? 'danger' : 'warning'">
              {{ row.overdue ? `已逾期${-row.daysLeft}天` : `${row.daysLeft}天后` }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div style="margin-bottom:16px;display:flex;justify-content:space-between;align-items:center">
      <h3>儿童档案</h3>
      <el-button type="primary" @click="openCreate">新建档案</el-button>
    </div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="姓名" />
      <el-table-column prop="disorderType" label="障碍类型" />
      <el-table-column prop="guardianUserId" label="监护人(家长ID)" />
      <el-table-column label="操作" width="340">
        <template #default="{ row }">
          <el-button size="small" @click="goDetail(row)">详情</el-button>
          <el-button size="small" @click="goAssessment(row)">评估</el-button>
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑档案' : '新建档案'" width="420px">
      <el-form label-width="90px">
        <el-form-item label="姓名">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="障碍类型">
          <el-input v-model="form.disorderType" placeholder="如 ASD" />
        </el-form-item>
        <el-form-item v-if="!editing" label="家长账号ID">
          <el-input v-model="form.guardianUserId" placeholder="可空;家长用户的 id" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listChildren, createChild, updateChild, deleteChild, listReminders } from '../api/children'

const rows = ref([])
const reminders = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref(false)
const editId = ref(null)
const form = reactive({ name: '', disorderType: '', guardianUserId: '' })
const router = useRouter()

const REMINDER_TYPE_LABELS = { REASSESS: '复评提醒', IEP_DUE: 'IEP到期' }
const reminderTypeLabel = (t) => REMINDER_TYPE_LABELS[t] || t

async function load() {
  loading.value = true
  try {
    rows.value = await listChildren()
    try { reminders.value = await listReminders() } catch (e) { reminders.value = [] }
  } finally {
    loading.value = false
  }
}
onMounted(load)

function goDetailById(row) {
  router.push(`/children/${row.childId}`)
}

function openCreate() {
  editing.value = false
  editId.value = null
  form.name = ''
  form.disorderType = ''
  form.guardianUserId = ''
  dialogVisible.value = true
}
function openEdit(row) {
  editing.value = true
  editId.value = row.id
  form.name = row.name
  form.disorderType = row.disorderType
  dialogVisible.value = true
}
async function onSubmit() {
  if (!form.name || !form.disorderType) {
    ElMessage.warning('请填写姓名和障碍类型')
    return
  }
  try {
    if (editing.value) {
      await updateChild(editId.value, { name: form.name, disorderType: form.disorderType })
    } else {
      const payload = { name: form.name, disorderType: form.disorderType }
      if (form.guardianUserId) payload.guardianUserId = Number(form.guardianUserId)
      await createChild(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } catch (e) { /* 拦截器已提示 */ }
}
async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除 ${row.name} 的档案?`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteChild(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) { /* 拦截器已提示 */ }
}
function goAssessment(row) {
  router.push({ path: '/assessment', query: { childId: row.id, childName: row.name } })
}
function goDetail(row) {
  router.push(`/children/${row.id}`)
}
</script>
