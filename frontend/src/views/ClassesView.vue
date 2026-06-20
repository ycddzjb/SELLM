<template>
  <div>
    <el-card style="margin-bottom:24px">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <span>班级管理(本机构)</span>
          <el-button type="primary" size="small" @click="openCreate">新建班级</el-button>
        </div>
      </template>
      <el-table :data="classes" size="small">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="班级名称" />
        <el-table-column label="学生障碍类型">
          <template #default="{ row }">{{ disorderCsvToLabels(row.disorderTypes) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!classes.length" description="暂无班级,点击右上角新建" :image-size="60" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑班级' : '新建班级'" width="480px">
      <el-form label-width="100px">
        <el-form-item label="班级名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="障碍类型">
          <el-select v-model="form.disorderCodes" multiple placeholder="可多选" style="width:100%">
            <el-option v-for="d in DISORDER_TYPES" :key="d.code" :label="d.label" :value="d.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listClasses, createClass, updateClass, deleteClass } from '../api/classes'
import { DISORDER_TYPES, disorderCsvToLabels } from '../api/meta'

const classes = ref([])
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = reactive({ name: '', disorderCodes: [] })

async function load() {
  try { classes.value = await listClasses() } catch (e) {}
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.disorderCodes = []
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.name = row.name
  form.disorderCodes = row.disorderTypes ? row.disorderTypes.split(',').map((c) => c.trim()) : []
  dialogVisible.value = true
}

async function onSave() {
  if (!form.name) { ElMessage.warning('请填写班级名称'); return }
  saving.value = true
  const payload = { name: form.name, disorderTypes: form.disorderCodes.join(',') }
  try {
    if (editingId.value) {
      await updateClass(editingId.value, payload)
      ElMessage.success('班级已更新')
    } else {
      await createClass(payload)
      ElMessage.success('班级已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {} finally { saving.value = false }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除班级「${row.name}」?`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteClass(row.id)
    ElMessage.success('班级已删除')
    await load()
  } catch (e) {}
}

onMounted(load)
</script>
