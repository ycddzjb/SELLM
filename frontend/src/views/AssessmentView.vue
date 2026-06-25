<template>
  <div>
    <h3>评估</h3>
    <el-form label-width="120px" style="max-width:640px">
      <el-form-item label="儿童ID">
        <el-input v-model="childId" placeholder="从儿童档案页点'评估'带入,或手填" />
      </el-form-item>
      <el-form-item label="选择量表">
        <el-select v-model="scaleId" placeholder="选择量表" style="width:100%" @change="onScaleChange">
          <el-option
            v-for="s in scales"
            :key="s.scaleId"
            :label="`${s.name}(${disorderLabel(s.disorderType)})`"
            :value="s.scaleId"
          />
        </el-select>
        <div style="margin-top:6px">
          <el-switch v-model="showAllScales" @change="reloadScales" />
          <span style="margin-left:8px;color:#909399;font-size:12px">
            {{ showAllScales ? '显示全部量表' : '仅显示按障碍类型推荐的量表' }}
          </span>
        </div>
      </el-form-item>

      <template v-if="items.length">
        <el-divider>量表作答</el-divider>
        <el-form-item v-for="item in items" :key="item.itemId" :label="item.stem">
          <el-rate v-model="answers[item.itemId]" :max="item.maxScore || 4" show-score />
        </el-form-item>

        <el-divider>AI 辅助评分(可选)</el-divider>
        <el-alert type="warning" :closable="false" show-icon style="margin-bottom:12px"
          title="AI 建议仅供参考,须教师确认。上传含儿童影像请确保已获监护人同意。" />
        <el-form-item label="训练笔记">
          <el-input v-model="aiNote" type="textarea" :rows="2" placeholder="描述课堂/干预表现(可选)" />
        </el-form-item>
        <el-form-item label="上传素材">
          <input type="file" @change="onFilePick" />
          <span v-if="aiFile" style="margin-left:8px;color:#909399">{{ aiFile.name }}</span>
        </el-form-item>
        <el-button :loading="aiLoading" @click="onAnalyze">获取 AI 评分建议</el-button>
        <div v-if="suggestions.length" style="margin-top:12px">
          <el-table :data="suggestions" size="small" border>
            <el-table-column label="指标">
              <template #default="{ row }">{{ stemOf(row.itemId) }}</template>
            </el-table-column>
            <el-table-column prop="suggestedScore" label="建议分" width="90" />
            <el-table-column prop="reason" label="理由" />
          </el-table>
          <el-button type="primary" link style="margin-top:8px" @click="adoptSuggestions">采纳建议填入评分</el-button>
        </div>

        <el-divider />
        <el-button type="primary" :loading="loading" @click="onSubmit">提交评估</el-button>
      </template>
      <el-empty v-else-if="scaleId" description="该量表暂无题目" :image-size="60" />
    </el-form>

    <el-card v-if="result" style="margin-top:20px;max-width:640px">
      <h4>评估结果</h4>
      <p>总分:{{ result.totalScore }}</p>
      <p>分段:{{ result.bandLabel }}</p>
      <p>解读:{{ result.interpretation }}</p>
      <el-button type="success" @click="goReport">基于此评估生成报告</el-button>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { submitAssessment } from '../api/assessments'
import { listScales, getScale } from '../api/scales'
import { recommendedScales } from '../api/children'
import { uploadMedia, analyzeMedia } from '../api/media'
import { disorderLabel } from '../api/meta'

const route = useRoute()
const router = useRouter()
const childId = ref(route.query.childId || '')

const scales = ref([])
const scaleId = ref('')
const showAllScales = ref(false)
const items = ref([])
const answers = reactive({})
const loading = ref(false)
const result = ref(null)

// AI 辅助评分
const aiNote = ref('')
const aiFile = ref(null)
const aiLoading = ref(false)
const suggestions = ref([])

function onFilePick(e) {
  aiFile.value = e.target.files && e.target.files[0] ? e.target.files[0] : null
}

function stemOf(itemId) {
  const it = items.value.find((i) => i.itemId === itemId)
  return it ? it.stem : itemId
}

async function onAnalyze() {
  if (!childId.value) { ElMessage.warning('请填写儿童ID'); return }
  if (!scaleId.value) { ElMessage.warning('请先选择量表'); return }
  if (!aiFile.value && !aiNote.value.trim()) { ElMessage.warning('请上传素材或填写训练笔记'); return }
  aiLoading.value = true
  try {
    const fd = new FormData()
    if (aiFile.value) {
      fd.append('file', aiFile.value)
      fd.append('mediaType', aiFile.value.type.startsWith('video') ? 'VIDEO' : 'IMAGE')
    } else {
      fd.append('mediaType', 'NOTE')
    }
    if (aiNote.value.trim()) fd.append('noteText', aiNote.value.trim())
    fd.append('scaleId', scaleId.value)
    const mediaId = await uploadMedia(Number(childId.value), fd)
    suggestions.value = await analyzeMedia(Number(childId.value), mediaId)
    ElMessage.success('已生成 AI 建议,请确认后采纳')
  } catch (e) { /* 拦截器已提示 */ } finally {
    aiLoading.value = false
  }
}

function adoptSuggestions() {
  suggestions.value.forEach((s) => {
    if (s.itemId in answers) answers[s.itemId] = s.suggestedScore
  })
  ElMessage.success('已填入评分,可手动调整后提交')
}

async function reloadScales() {
  try {
    if (!showAllScales.value && childId.value) {
      const rec = await recommendedScales(Number(childId.value))
      if (rec && rec.length) { scales.value = rec; return }
      showAllScales.value = true
      ElMessage.info('该儿童无对应推荐量表,已显示全部')
    }
    scales.value = await listScales()
  } catch (e) { scales.value = [] }
}

async function onScaleChange(id) {
  // 清空旧题目与作答
  items.value = []
  Object.keys(answers).forEach((k) => delete answers[k])
  if (!id) return
  try {
    const detail = await getScale(id)
    // 按 sortOrder 排序(后端已排,前端兜底)
    items.value = (detail.items || []).slice().sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    items.value.forEach((it) => { answers[it.itemId] = 0 })
  } catch (e) {}
}

async function onSubmit() {
  if (!childId.value) { ElMessage.warning('请填写儿童ID'); return }
  if (!scaleId.value) { ElMessage.warning('请选择量表'); return }
  loading.value = true
  try {
    const payload = {
      childId: Number(childId.value),
      scaleId: scaleId.value,
      answers: items.value.map((it) => ({ itemId: it.itemId, score: answers[it.itemId] }))
    }
    result.value = await submitAssessment(payload)
    ElMessage.success('评估已提交')
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

function goReport() {
  router.push({ path: '/report', query: { assessmentId: result.value.id } })
}

onMounted(reloadScales)
</script>
