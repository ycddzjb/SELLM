<template>
  <div>
    <h3>教学档案</h3>
    <p style="color:#888;margin-top:-6px">已定稿归档的教案与课件,可查看与导出。</p>
    <el-table :data="rows" size="small" style="max-width:920px">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ typeLabel(row.contentType) }}</template>
      </el-table-column>
      <el-table-column prop="title" label="标题" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="view = row">查看</el-button>
          <el-button v-if="row.contentType === 'COURSEWARE'" link type="warning" @click="exportPptRow(row)">导出 PPT</el-button>
          <el-button v-else link type="success" @click="exportWordRow(row)">导出 Word</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!rows.length" description="暂无归档内容" :image-size="60" />

    <el-card v-if="view" style="max-width:920px;margin-top:16px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>{{ view.title }} · {{ typeLabel(view.contentType) }}</span>
        <el-tag type="success">已归档</el-tag>
      </div>
      <pre class="box">{{ view.content }}</pre>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listContents } from '../api/teaching'
import { exportWord, exportPpt } from '../utils/exporter'

const rows = ref([])
const view = ref(null)
const TYPE_LABELS = { PLAN: '训练方案', LESSON: '教案', COURSEWARE: '课件', EXERCISE: '习题' }
const typeLabel = (t) => TYPE_LABELS[t] || t

onMounted(load)
async function load() {
  try {
    const [lessons, cw] = await Promise.all([listContents('LESSON'), listContents('COURSEWARE')])
    rows.value = [...lessons, ...cw].filter(c => c.status === 'FINALIZED').sort((a, b) => b.id - a.id)
  } catch (e) {}
}
function exportWordRow(row) {
  try { exportWord(row.title, row.content) } catch (e) { ElMessage.error('导出失败') }
}
function exportPptRow(row) {
  try { exportPpt(row.title, row.content) } catch (e) { ElMessage.error('导出失败') }
}
</script>

<style scoped>
.box { white-space:pre-wrap; background:#f7f7f7; padding:12px; border-radius:4px; max-height:360px; overflow:auto; }
</style>
