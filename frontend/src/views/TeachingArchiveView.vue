<template>
  <div>
    <h3>教学档案</h3>
    <p style="color:#888;margin-top:-6px">已定稿归档的教案与课件,可查看、导出与删除。</p>
    <el-table :data="rows" size="small" style="max-width:920px">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ typeLabel(row.contentType) }}</template>
      </el-table-column>
      <el-table-column prop="title" label="标题" />
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button link type="primary" @click="view = row">查看</el-button>
          <el-button v-if="row.contentType === 'COURSEWARE'" link type="warning" @click="exportPptRow(row)">导出 PPT</el-button>
          <el-button v-else link type="success" @click="exportWordRow(row)">导出 Word</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!rows.length" description="暂无归档内容" :image-size="60" />

    <el-card v-if="view" style="max-width:920px;margin-top:16px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>{{ view.title }} · {{ typeLabel(view.contentType) }}</span>
        <el-tag type="success">已归档</el-tag>
      </div>
      <pre class="box">{{ viewText(view) }}</pre>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listContents, deleteContent } from '../api/teaching'
import { exportWord, exportCoursewarePptx } from '../utils/exporter'

const rows = ref([])
const view = ref(null)
const TYPE_LABELS = { PLAN: '训练方案', LESSON: '教案', COURSEWARE: '课件', EXERCISE: '习题' }
const typeLabel = (t) => TYPE_LABELS[t] || t

// 课件存的是 PPT 结构 JSON,查看时展示页标题概览;教案等展示正文
function viewText(row) {
  if (row.contentType === 'COURSEWARE') {
    try {
      const d = JSON.parse(row.content)
      if (d && Array.isArray(d.slides)) {
        return d.slides.map((s, i) => `${i + 1}. ${s.type === 'cover' ? '封面:' + (s.title || '') : (s.heading || '')}`).join('\n')
          + '\n\n(PPT 课件,点"导出 PPT"下载完整暖色课件)'
      }
    } catch (e) {}
  }
  return row.content
}

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
  try { exportCoursewarePptx(row.title, row.content) } catch (e) { ElMessage.error('导出失败') }
}
async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.title}」?删除后不可恢复。`, '确认删除', { type: 'warning' })
  } catch { return }
  try {
    await deleteContent(row.id)
    ElMessage.success('已删除')
    if (view.value && view.value.id === row.id) view.value = null
    load()
  } catch (e) {}
}
</script>

<style scoped>
.box { white-space:pre-wrap; background:#f7f7f7; padding:12px; border-radius:4px; max-height:360px; overflow:auto; }
</style>
