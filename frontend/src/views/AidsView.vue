<template>
  <div>
    <h3>智能教具 · 推荐与文生素材</h3>
    <el-tabs v-model="tab">
      <!-- 教具推荐 -->
      <el-tab-pane label="教具推荐" name="recommend">
        <el-card style="max-width:860px">
          <el-select v-model="disorderType" placeholder="按障碍类型(空=全部)" clearable style="width:240px">
            <el-option v-for="d in disorderOptions" :key="d.code" :label="d.label" :value="d.code" />
          </el-select>
          <el-button type="primary" :loading="recLoading" style="margin-left:12px" @click="onRecommend">查询推荐</el-button>
          <el-table :data="aids" style="margin-top:16px" size="small">
            <el-table-column prop="name" label="教具名" width="160" />
            <el-table-column prop="category" label="类别" width="120" />
            <el-table-column prop="usageGuide" label="使用建议" />
          </el-table>
          <el-empty v-if="recLoaded && !aids.length" description="该类型暂无匹配教具" />
        </el-card>
      </el-tab-pane>

      <!-- 文生素材 -->
      <el-tab-pane label="文生素材" name="asset">
        <el-card style="max-width:860px;margin-bottom:16px">
          <el-select v-model="assetType" style="width:200px">
            <el-option label="教学插图" value="IMAGE" />
            <el-option label="社交故事绘本" value="PICTUREBOOK" />
            <el-option label="听觉训练音频" value="AUDIO" />
            <el-option label="教学短视频" value="VIDEO" />
          </el-select>
          <el-input v-model="prompt" type="textarea" :rows="3" placeholder="描述想要的素材" style="margin-top:8px" />
          <el-input v-model="assetSubjects" placeholder="脱敏屏蔽名(逗号分隔,可空)" style="margin-top:8px" />
          <div style="margin-top:12px">
            <el-button type="primary" :loading="submitting" @click="onSubmit">提交生成</el-button>
            <el-button @click="loadAssets">刷新列表</el-button>
          </div>
          <div v-if="task" style="margin-top:16px">
            <el-tag :type="taskTagType">{{ task.status }}</el-tag>
            <span v-if="task.status==='RUNNING'||task.status==='PENDING'" style="margin-left:8px;color:#888">生成中…</span>
            <span v-if="task.status==='FAILED'" style="margin-left:8px;color:#f56c6c">{{ task.error }}</span>
            <div v-if="previewUrl" style="margin-top:12px"><el-image :src="previewUrl" style="max-width:360px" fit="contain" /></div>
          </div>
        </el-card>
        <el-table :data="assets" style="max-width:860px" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }"><el-tag :type="row.status==='SUCCESS'?'success':(row.status==='FAILED'?'danger':'info')">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }"><el-button v-if="row.status==='SUCCESS'" link type="primary" @click="preview(row)">预览</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { DISORDER_TYPES } from '../api/meta'
import { recommendAids, submitAsset, pollAssetTask, listAssets, fetchAssetBlobUrl } from '../api/aidsAgent'

const tab = ref('recommend')
const disorderOptions = DISORDER_TYPES
const disorderType = ref('')
const aids = ref([])
const recLoading = ref(false), recLoaded = ref(false)

const assetType = ref('IMAGE')
const prompt = ref('')
const assetSubjects = ref('')
const submitting = ref(false)
const task = ref(null)
const assets = ref([])
const previewUrl = ref('')
let timer = null

onMounted(loadAssets)
onUnmounted(() => { if (timer) clearInterval(timer) })

function splitNames(raw) { return (raw || '').split(/[,，]/).map(s => s.trim()).filter(Boolean) }

const taskTagType = computed(() => task.value?.status === 'SUCCESS' ? 'success' : (task.value?.status === 'FAILED' ? 'danger' : 'info'))

async function onRecommend() {
  recLoading.value = true
  try { aids.value = await recommendAids(disorderType.value); recLoaded.value = true } catch (e) {} finally { recLoading.value = false }
}

async function loadAssets() { try { assets.value = await listAssets() } catch (e) {} }

async function onSubmit() {
  if (!prompt.value.trim()) { ElMessage.warning('请填描述'); return }
  submitting.value = true
  previewUrl.value = ''
  try {
    const r = await submitAsset({ type: assetType.value, prompt: prompt.value, subjectNames: splitNames(assetSubjects.value) })
    task.value = { taskId: r.taskId, status: 'PENDING' }
    startPoll(r.taskId)
  } catch (e) {} finally { submitting.value = false }
}

function startPoll(taskId) {
  if (timer) clearInterval(timer)
  timer = setInterval(async () => {
    try {
      const s = await pollAssetTask(taskId)
      task.value = { taskId, ...s }
      if (s.status === 'SUCCESS' || s.status === 'FAILED') {
        clearInterval(timer); timer = null; loadAssets()
        if (s.status === 'SUCCESS') previewUrl.value = await fetchAssetBlobUrl(taskId)
      }
    } catch (e) { clearInterval(timer); timer = null }
  }, 1500)
}

async function preview(row) {
  try { previewUrl.value = await fetchAssetBlobUrl(row.id); task.value = { taskId: row.id, status: row.status } } catch (e) {}
}
</script>
