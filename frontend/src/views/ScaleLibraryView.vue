<template>
  <div>
    <el-card style="margin-bottom:24px">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <span>量表库管理</span>
          <div style="display:flex;gap:12px;align-items:center">
            <el-select v-model="filterType" placeholder="按品类筛选" clearable style="width:180px" @change="load">
              <el-option v-for="d in DISORDER_TYPES" :key="d.code" :label="d.label" :value="d.code" />
            </el-select>
            <el-button type="primary" size="small" @click="openCreate">新建量表</el-button>
          </div>
        </div>
      </template>
      <el-table :data="scales" size="small">
        <el-table-column prop="scaleId" label="量表ID" width="140" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column label="品类">
          <template #default="{ row }">{{ disorderLabel(row.disorderType) }}</template>
        </el-table-column>
        <el-table-column prop="description" label="简介" show-overflow-tooltip />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row.scaleId)">编辑</el-button>
            <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!scales.length" description="暂无量表,点击右上角新建" :image-size="60" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑量表' : '新建量表'" width="760px" top="5vh">
      <el-form label-width="90px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-form-item label="量表ID">
          <el-input v-model="form.scaleId" :disabled="!!editingId" placeholder="英文/数字,如 sensory_v1" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="form.version" placeholder="如 v1" />
        </el-form-item>
        <el-form-item label="品类">
          <el-select v-model="form.disorderType" placeholder="选择障碍品类" style="width:100%">
            <el-option v-for="d in DISORDER_TYPES" :key="d.code" :label="d.label" :value="d.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>

        <el-divider content-position="left">
          题目
          <el-button size="small" link type="primary" @click="addItem">+ 添加题目</el-button>
        </el-divider>
        <div v-for="(it, idx) in form.items" :key="idx" style="display:flex;gap:8px;margin-bottom:8px;align-items:center">
          <el-input v-model="it.itemId" placeholder="题目ID" style="width:110px" />
          <el-input v-model="it.stem" placeholder="题干" style="flex:1" />
          <el-input v-model="it.dimension" placeholder="维度" style="width:110px" />
          <el-input-number v-model="it.sortOrder" :min="0" controls-position="right" style="width:110px" placeholder="排序" />
          <el-input-number v-model="it.maxScore" :min="1" controls-position="right" style="width:120px" placeholder="满分" />
          <el-button size="small" type="danger" link @click="form.items.splice(idx, 1)">删除</el-button>
        </div>

        <el-divider content-position="left">
          分段(计分规则)
          <el-button size="small" link type="primary" @click="addBand">+ 添加分段</el-button>
        </el-divider>
        <div v-for="(b, idx) in form.bands" :key="idx" style="display:flex;gap:8px;margin-bottom:8px;align-items:center">
          <el-input-number v-model="b.lowerBound" controls-position="right" style="width:120px" placeholder="下限" />
          <el-input-number v-model="b.upperBound" controls-position="right" style="width:120px" placeholder="上限" />
          <el-input v-model="b.label" placeholder="分段标签" style="width:140px" />
          <el-input v-model="b.interpretation" placeholder="解读" style="flex:1" />
          <el-button size="small" type="danger" link @click="form.bands.splice(idx, 1)">删除</el-button>
        </div>
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
import { listScales, getScale, createScale, updateScale, deleteScale } from '../api/scales'
import { DISORDER_TYPES, disorderLabel } from '../api/meta'

const scales = ref([])
const filterType = ref('')
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = reactive({
  scaleId: '', name: '', version: 'v1', disorderType: '', description: '',
  items: [], bands: []
})

async function load() {
  try { scales.value = await listScales(filterType.value || undefined) } catch (e) {}
}

function resetForm() {
  form.scaleId = ''
  form.name = ''
  form.version = 'v1'
  form.disorderType = ''
  form.description = ''
  form.items = []
  form.bands = []
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

async function openEdit(scaleId) {
  try {
    const d = await getScale(scaleId)
    editingId.value = scaleId
    form.scaleId = d.scaleId
    form.name = d.name
    form.version = d.version
    form.disorderType = d.disorderType || ''
    form.description = d.description || ''
    form.items = (d.items || []).map((it) => ({ ...it }))
    form.bands = (d.bands || []).map((b) => ({ ...b }))
    dialogVisible.value = true
  } catch (e) {}
}

function addItem() {
  form.items.push({ itemId: '', stem: '', dimension: '', sortOrder: form.items.length + 1, maxScore: 4 })
}
function addBand() {
  form.bands.push({ lowerBound: 0, upperBound: 0, label: '', interpretation: '' })
}

async function onSave() {
  if (!form.scaleId || !form.name) { ElMessage.warning('请填写量表ID和名称'); return }
  if (!form.disorderType) { ElMessage.warning('请选择品类'); return }
  saving.value = true
  const payload = {
    scaleId: form.scaleId, name: form.name, version: form.version,
    disorderType: form.disorderType, description: form.description,
    items: form.items, bands: form.bands
  }
  try {
    if (editingId.value) {
      await updateScale(editingId.value, payload)
      ElMessage.success('量表已更新')
    } else {
      await createScale(payload)
      ElMessage.success('量表已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {} finally { saving.value = false }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除量表「${row.name}」?其题目与分段将一并删除。`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteScale(row.scaleId)
    ElMessage.success('量表已删除')
    await load()
  } catch (e) {}
}

onMounted(load)
</script>
