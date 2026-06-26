<template>
  <div>
    <h3>训练对比评估</h3>
    <el-alert type="info" :closable="false" show-icon style="max-width:960px;margin-bottom:12px"
      title="按训练周期记录多模态训练数据与指标得分,生成阶段评估(量化提升对比 + AI 适配性建议),据此优化出新版 IEP。AI 仅产草案,需教师定稿。" />

    <!-- 1. 周期 -->
    <el-card style="max-width:960px;margin-bottom:16px">
      <el-form label-width="100px" :inline="true">
        <el-form-item label="儿童ID">
          <el-input v-model="childId" placeholder="儿童档案 ID" style="width:160px" />
        </el-form-item>
        <el-form-item label="诊断ID">
          <el-input v-model="diagnosisId" placeholder="可选" style="width:120px" />
        </el-form-item>
        <el-form-item label="IEP ID">
          <el-input v-model="iepId" placeholder="可选" style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="cycleLoading" @click="onCreateCycle">新建周期</el-button>
          <el-button @click="loadCycles">刷新周期列表</el-button>
        </el-form-item>
      </el-form>
      <el-table v-if="cycles.length" :data="cycles" size="small" @row-click="selectCycle" highlight-current-row>
        <el-table-column prop="seq" label="阶段" width="70" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }"><el-tag :type="row.status==='ACTIVE'?'success':'info'">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="70" />
      </el-table>
    </el-card>

    <!-- 2. 训练数据 -->
    <el-card v-if="cycle" style="max-width:960px;margin-bottom:16px">
      <div style="font-weight:600;margin-bottom:10px">周期 #{{ cycle.id }}(阶段{{ cycle.seq }}) · 训练数据</div>
      <el-form label-width="100px">
        <el-form-item label="素材类型">
          <el-select v-model="mediaType" style="width:180px">
            <el-option label="文本笔记" value="TEXT" />
            <el-option label="图片" value="IMAGE" />
            <el-option label="视频" value="VIDEO" />
            <el-option label="语音" value="AUDIO" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="mediaType !== 'TEXT'" label="文件">
          <input ref="fileInput" type="file" :accept="acceptOf" />
        </el-form-item>
        <el-form-item label="文字描述">
          <el-input v-model="noteText" type="textarea" :rows="2" placeholder="训练表现描述" />
        </el-form-item>
        <el-form-item label="指标得分">
          <el-input v-model="scoresRaw" type="textarea" :rows="2"
            placeholder='JSON:[{"item":"剥珠","score":3,"maxScore":4}]' />
        </el-form-item>
        <el-form-item>
          <el-button :loading="recordLoading" @click="onAddRecord">挂训练数据</el-button>
          <el-button v-if="cycle.status==='ACTIVE'" @click="onCloseCycle">关闭周期</el-button>
        </el-form-item>
      </el-form>
      <el-table v-if="records.length" :data="records" size="small">
        <el-table-column prop="mediaType" label="类型" width="80" />
        <el-table-column prop="scores" label="指标得分" show-overflow-tooltip />
        <el-table-column prop="transcript" label="识别" show-overflow-tooltip />
      </el-table>
      <div style="margin-top:12px">
        <el-button type="primary" :loading="evalLoading" @click="onGenEval">生成阶段评估</el-button>
      </div>
    </el-card>

    <!-- 3. 阶段评估 -->
    <el-card v-if="stageEval" style="max-width:960px;margin-bottom:16px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>阶段评估 #{{ stageEval.id }}</span>
        <el-tag :type="stageEval.status==='FINALIZED'?'success':'info'">{{ stageEval.status }}</el-tag>
      </div>
      <el-divider>量化对比(delta &gt; 0 进步 / reached 达标)</el-divider>
      <pre class="box">{{ prettyDelta }}</pre>
      <el-divider>AI 评估报告草案</el-divider>
      <el-input v-model="evalContent" type="textarea" :rows="6" :disabled="stageEval.status==='FINALIZED'" />
      <div style="margin-top:12px">
        <el-button v-if="stageEval.status!=='FINALIZED'" :loading="editLoading" @click="onEditEval">保存</el-button>
        <el-button v-if="stageEval.status!=='FINALIZED'" type="primary" :loading="finLoading" @click="onFinalizeEval">定稿</el-button>
        <el-button v-if="stageEval.status==='FINALIZED'" type="success" :loading="nextLoading" @click="onNextIep">据此生成新版 IEP →</el-button>
      </div>
      <div v-if="nextIep" style="margin-top:12px;padding:12px;background:#f0f9eb;border-radius:4px">
        <b>新版 IEP #{{ nextIep.id }}(DRAFT,关联周期{{ nextIep.cycleId }})</b>
        <pre class="box">{{ nextIep.draft }}</pre>
        <el-button size="small" @click="goIep(nextIep.id)">去 IEP 页定稿</el-button>
      </div>
    </el-card>

    <!-- 4. 纵向对比 -->
    <el-card v-if="compareRows.length" style="max-width:960px">
      <div style="font-weight:600;margin-bottom:10px">纵向对比(各周期阶段评估)</div>
      <el-table :data="compareRows" size="small">
        <el-table-column prop="evalId" label="评估ID" width="90" />
        <el-table-column prop="met" label="达标项" width="90" />
        <el-table-column prop="improved" label="进步项" width="90" />
        <el-table-column prop="total" label="指标数" width="90" />
      </el-table>
    </el-card>
  </div>
</template>
<!-- SCRIPT_PLACEHOLDER -->

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createCycle, listCyclesByChild, closeCycle, addRecord, listRecords,
  generateStageEval, editStageEval, finalizeStageEval, compareByChild, generateNextIep
} from '../api/training'

const route = useRoute()
const router = useRouter()

const childId = ref(route.query.childId || '')
const diagnosisId = ref('')
const iepId = ref('')
const cycles = ref([])
const cycle = ref(null)

const mediaType = ref('TEXT')
const noteText = ref('')
const scoresRaw = ref('')
const fileInput = ref(null)
const records = ref([])

const stageEval = ref(null)
const evalContent = ref('')
const nextIep = ref(null)
const compareRows = ref([])

const cycleLoading = ref(false)
const recordLoading = ref(false)
const evalLoading = ref(false)
const editLoading = ref(false)
const finLoading = ref(false)
const nextLoading = ref(false)

const acceptOf = computed(() => {
  if (mediaType.value === 'IMAGE') return 'image/*'
  if (mediaType.value === 'VIDEO') return 'video/*'
  if (mediaType.value === 'AUDIO') return 'audio/*'
  return '*/*'
})

const prettyDelta = computed(() => {
  if (!stageEval.value || !stageEval.value.deltaSummary) return '(无)'
  try { return JSON.stringify(JSON.parse(stageEval.value.deltaSummary), null, 2) }
  catch (e) { return stageEval.value.deltaSummary }
})

async function loadCycles() {
  if (!childId.value) { ElMessage.warning('请填写儿童ID'); return }
  try { cycles.value = await listCyclesByChild(Number(childId.value)); loadCompare() } catch (e) {}
}

async function onCreateCycle() {
  if (!childId.value) { ElMessage.warning('请填写儿童ID'); return }
  cycleLoading.value = true
  try {
    const c = await createCycle({
      childId: Number(childId.value),
      diagnosisId: diagnosisId.value ? Number(diagnosisId.value) : null,
      iepId: iepId.value ? Number(iepId.value) : null,
      title: '阶段训练'
    })
    ElMessage.success('已建周期 #' + c.id + '(阶段' + c.seq + ')')
    await loadCycles()
    selectCycle(c)
  } catch (e) {} finally { cycleLoading.value = false }
}

async function selectCycle(row) {
  cycle.value = row
  stageEval.value = null; nextIep.value = null
  try { records.value = await listRecords(row.id) } catch (e) { records.value = [] }
}

async function onAddRecord() {
  if (mediaType.value === 'TEXT' && !noteText.value.trim()) { ElMessage.warning('文本类型请填描述'); return }
  const fd = new FormData()
  fd.append('mediaType', mediaType.value)
  if (noteText.value) fd.append('noteText', noteText.value)
  if (scoresRaw.value) fd.append('scores', scoresRaw.value)
  const f = fileInput.value && fileInput.value.files && fileInput.value.files[0]
  if (mediaType.value !== 'TEXT') {
    if (!f) { ElMessage.warning('请选择文件'); return }
    fd.append('file', f)
  }
  recordLoading.value = true
  try {
    await addRecord(cycle.value.id, fd)
    ElMessage.success('已挂训练数据')
    noteText.value = ''; scoresRaw.value = ''
    if (fileInput.value) fileInput.value.value = ''
    records.value = await listRecords(cycle.value.id)
  } catch (e) {} finally { recordLoading.value = false }
}

async function onCloseCycle() {
  try { cycle.value = await closeCycle(cycle.value.id); ElMessage.success('周期已关闭'); loadCycles() } catch (e) {}
}

async function onGenEval() {
  evalLoading.value = true
  try {
    stageEval.value = await generateStageEval(cycle.value.id)
    evalContent.value = stageEval.value.draft || ''
    nextIep.value = null
    ElMessage.success('已生成阶段评估'); loadCompare()
  } catch (e) {} finally { evalLoading.value = false }
}

async function onEditEval() {
  editLoading.value = true
  try { stageEval.value = await editStageEval(stageEval.value.id, evalContent.value); ElMessage.success('已保存') }
  catch (e) {} finally { editLoading.value = false }
}

async function onFinalizeEval() {
  finLoading.value = true
  try { stageEval.value = await finalizeStageEval(stageEval.value.id, evalContent.value); ElMessage.success('已定稿') }
  catch (e) {} finally { finLoading.value = false }
}

async function onNextIep() {
  nextLoading.value = true
  try { nextIep.value = await generateNextIep(cycle.value.id); ElMessage.success('已生成新版 IEP 草案') }
  catch (e) {} finally { nextLoading.value = false }
}

async function loadCompare() {
  if (!childId.value) return
  try {
    const evals = await compareByChild(Number(childId.value))
    compareRows.value = evals.map(e => {
      let d = {}; try { d = JSON.parse(e.deltaSummary || '{}') } catch (x) {}
      return { evalId: e.id, met: d.metItems ?? '-', improved: d.improvedItems ?? '-', total: d.totalItems ?? '-' }
    })
  } catch (e) {}
}

function goIep(id) { router.push({ path: '/iep', query: { iepId: id } }) }
</script>

<style scoped>
.box { white-space:pre-wrap; background:#f7f7f7; padding:12px; border-radius:4px; max-height:260px; overflow:auto; font-size:13px; }
</style>
