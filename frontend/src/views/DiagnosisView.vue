<template>
  <div>
    <h3>多模态诊断</h3>
    <el-alert type="info" :closable="false" show-icon style="max-width:900px;margin-bottom:12px"
      title="支持文本/图片/视频/语音多模态输入,结合量表知识库分析各维度能力等级、现存障碍、能力缺陷。AI 仅产草案,需教师定稿。上传含儿童影像/语音请确保已获监护人同意。" />

    <!-- 1. 建诊断 -->
    <el-card style="max-width:900px;margin-bottom:16px">
      <el-form label-width="110px">
        <el-form-item label="儿童ID">
          <el-input v-model="childId" placeholder="儿童档案 ID" style="width:220px" />
        </el-form-item>
        <el-form-item label="关联量表">
          <el-select v-model="scaleId" placeholder="可选,按维度引导" clearable style="width:260px">
            <el-option v-for="s in scales" :key="s.scaleId" :label="s.name" :value="s.scaleId" />
          </el-select>
        </el-form-item>
        <el-form-item label="训练表现">
          <el-input v-model="structuredInput" type="textarea" :rows="3"
            placeholder='结构化训练表现,如:{"剥珠正确率":"40%","眼神互动":"偶尔"}' />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="createLoading" @click="onCreate" :disabled="!!diagId">
            {{ diagId ? '诊断已建 #' + diagId : '建立诊断' }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 2. 挂多模态素材 -->
    <el-card v-if="diagId" style="max-width:900px;margin-bottom:16px">
      <div style="font-weight:600;margin-bottom:10px">多模态素材(挂一条识别一条)</div>
      <el-form label-width="110px">
        <el-form-item label="素材类型">
          <el-select v-model="mediaType" style="width:200px">
            <el-option label="文本笔记" value="TEXT" />
            <el-option label="图片" value="IMAGE" />
            <el-option label="视频" value="VIDEO" />
            <el-option label="语音" value="AUDIO" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="mediaType !== 'TEXT'" label="上传文件">
          <input ref="fileInput" type="file" :accept="acceptOf" />
        </el-form-item>
        <el-form-item label="文字描述">
          <el-input v-model="noteText" type="textarea" :rows="2" placeholder="训练表现文字描述(文本类型必填)" />
        </el-form-item>
        <el-form-item>
          <el-button :loading="mediaLoading" @click="onAddMedia">挂素材并识别</el-button>
        </el-form-item>
      </el-form>
      <el-table v-if="mediaList.length" :data="mediaList" size="small" style="margin-top:8px">
        <el-table-column prop="mediaType" label="类型" width="80" />
        <el-table-column prop="transcript" label="识别结果" show-overflow-tooltip />
      </el-table>
      <div style="margin-top:12px">
        <el-button type="primary" :loading="genLoading" @click="onGenerate">生成结构化诊断</el-button>
      </div>
    </el-card>

    <!-- 3. 诊断结果 -->
    <el-card v-if="diagnosis" style="max-width:900px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>诊断 #{{ diagnosis.id }}</span>
        <el-tag :type="diagnosis.status === 'FINALIZED' ? 'success' : 'info'">{{ diagnosis.status }}</el-tag>
      </div>
      <el-divider>能力维度(等级 / 现存障碍 / 能力缺陷)</el-divider>
      <pre class="box">{{ diagnosis.dimensions || '(无结构化维度)' }}</pre>
      <el-divider>诊断报告草案</el-divider>
      <el-input v-model="reportContent" type="textarea" :rows="6"
        :disabled="diagnosis.status === 'FINALIZED'" placeholder="在 AI 草案基础上编辑" />
      <div style="margin-top:12px">
        <el-button v-if="diagnosis.status !== 'FINALIZED'" :loading="editLoading" @click="onEdit">保存编辑</el-button>
        <el-button v-if="diagnosis.status !== 'FINALIZED'" type="primary" :loading="finLoading" @click="onFinalize">定稿</el-button>
        <el-button v-if="diagnosis.status === 'FINALIZED'" @click="onDownload">下载 PDF</el-button>
        <el-button v-if="diagnosis.status === 'FINALIZED'" type="success" @click="goIep">据此生成 IEP →</el-button>
      </div>
    </el-card>
  </div>
</template>
<!-- SCRIPT_PLACEHOLDER -->

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listScales } from '../api/scales'
import {
  createDiagnosis, addDiagnosisMedia, generateDiagnosis,
  editDiagnosis, finalizeDiagnosis, downloadDiagnosisPdf
} from '../api/diagnosis'
import { saveBlob } from '../api/familyIeps'

const route = useRoute()
const router = useRouter()

const childId = ref(route.query.childId || '')
const scaleId = ref('')
const structuredInput = ref('')
const scales = ref([])

const diagId = ref(null)
const mediaType = ref('TEXT')
const noteText = ref('')
const fileInput = ref(null)
const mediaList = ref([])

const diagnosis = ref(null)
const reportContent = ref('')

const createLoading = ref(false)
const mediaLoading = ref(false)
const genLoading = ref(false)
const editLoading = ref(false)
const finLoading = ref(false)

const acceptOf = computed(() => {
  if (mediaType.value === 'IMAGE') return 'image/*'
  if (mediaType.value === 'VIDEO') return 'video/*'
  if (mediaType.value === 'AUDIO') return 'audio/*'
  return '*/*'
})

onMounted(async () => {
  try { scales.value = await listScales() } catch (e) {}
})

async function onCreate() {
  if (!childId.value) { ElMessage.warning('请填写儿童ID'); return }
  createLoading.value = true
  try {
    const d = await createDiagnosis({
      childId: Number(childId.value),
      scaleId: scaleId.value || null,
      structuredInput: structuredInput.value || null
    })
    diagId.value = d.id
    ElMessage.success('诊断已建,可挂素材')
  } catch (e) {} finally { createLoading.value = false }
}

async function onAddMedia() {
  if (mediaType.value === 'TEXT' && !noteText.value.trim()) {
    ElMessage.warning('文本类型请填写文字描述'); return
  }
  const fd = new FormData()
  fd.append('mediaType', mediaType.value)
  if (noteText.value) fd.append('noteText', noteText.value)
  const f = fileInput.value && fileInput.value.files && fileInput.value.files[0]
  if (mediaType.value !== 'TEXT') {
    if (!f) { ElMessage.warning('请选择文件'); return }
    fd.append('file', f)
  }
  mediaLoading.value = true
  try {
    await addDiagnosisMedia(diagId.value, fd)
    ElMessage.success('已挂素材并识别')
    noteText.value = ''
    if (fileInput.value) fileInput.value.value = ''
    // 本地追加一条占位(识别结果以生成时聚合为准;此处仅提示已挂)
    mediaList.value.push({ mediaType: mediaType.value, transcript: '已上传,生成时聚合识别' })
  } catch (e) {} finally { mediaLoading.value = false }
}

async function onGenerate() {
  genLoading.value = true
  try {
    diagnosis.value = await generateDiagnosis(diagId.value, [])
    reportContent.value = diagnosis.value.draft || ''
    ElMessage.success('已生成结构化诊断')
  } catch (e) {} finally { genLoading.value = false }
}

async function onEdit() {
  if (!reportContent.value.trim()) { ElMessage.warning('报告不能为空'); return }
  editLoading.value = true
  try {
    diagnosis.value = await editDiagnosis(diagnosis.value.id, reportContent.value)
    ElMessage.success('已保存')
  } catch (e) {} finally { editLoading.value = false }
}

async function onFinalize() {
  finLoading.value = true
  try {
    diagnosis.value = await finalizeDiagnosis(diagnosis.value.id, reportContent.value)
    ElMessage.success('已定稿')
  } catch (e) {} finally { finLoading.value = false }
}

async function onDownload() {
  try {
    const blob = await downloadDiagnosisPdf(diagnosis.value.id)
    saveBlob(blob, `diagnosis-${diagnosis.value.id}.pdf`)
  } catch (e) {}
}

function goIep() {
  router.push({ path: '/iep', query: { diagnosisId: diagnosis.value.id } })
}
</script>

<style scoped>
.box { white-space:pre-wrap; background:#f7f7f7; padding:12px; border-radius:4px; max-height:280px; overflow:auto; }
</style>
