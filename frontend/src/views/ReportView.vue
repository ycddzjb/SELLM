<template>
  <div>
    <h3>评估报告</h3>
    <el-form label-width="120px" style="max-width:760px">
      <el-form-item label="评估ID">
        <el-input v-model="assessmentId" placeholder="从评估页带入,或手填">
          <template #append>
            <el-button :loading="genLoading" @click="onGenerate">生成报告草稿</el-button>
          </template>
        </el-input>
      </el-form-item>
    </el-form>

    <el-card v-if="report" style="margin-top:16px;max-width:760px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>报告 #{{ report.id }}</span>
        <el-tag :type="report.status === 'FINALIZED' ? 'success' : 'info'">{{ report.status }}</el-tag>
      </div>
      <el-divider>AI 草稿(仅供参考,需人工把关)</el-divider>
      <pre style="white-space:pre-wrap;background:#f7f7f7;padding:12px;border-radius:4px">{{ report.draft }}</pre>
      <el-divider>定稿内容</el-divider>
      <el-input v-model="finalContent" type="textarea" :rows="6"
                placeholder="在 AI 草稿基础上修改为最终报告" />
      <div style="margin-top:12px">
        <el-button type="primary" :loading="finLoading" @click="onFinalize">定稿</el-button>
        <el-button v-if="report.status === 'FINALIZED'" type="success" @click="goIep">基于此报告生成 IEP</el-button>
        <el-button v-if="report.status === 'FINALIZED'" @click="onDownload">下载 PDF</el-button>
      </div>
      <p v-if="report.finalizedContent" style="margin-top:12px;color:#666">
        已定稿内容:{{ report.finalizedContent }}
      </p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { generateReport, getReport, finalizeReport, downloadReportPdf } from '../api/reports'
import { saveBlob } from '../api/familyIeps'

const route = useRoute()
const router = useRouter()
const assessmentId = ref(route.query.assessmentId || '')
const report = ref(null)
const finalContent = ref('')
const genLoading = ref(false)
const finLoading = ref(false)

onMounted(() => {
  if (route.query.reportId) {
    loadReport(Number(route.query.reportId))
  }
})

async function loadReport(reportId) {
  genLoading.value = true
  try {
    report.value = await getReport(reportId)
    finalContent.value = report.value.finalizedContent || report.value.draft
  } catch (e) {} finally { genLoading.value = false }
}

async function onGenerate() {
  if (!assessmentId.value) { ElMessage.warning('请填写评估ID'); return }
  genLoading.value = true
  try {
    report.value = await generateReport(Number(assessmentId.value))
    finalContent.value = report.value.draft  // 预填草稿供编辑
    ElMessage.success('已生成草稿')
  } catch (e) {} finally { genLoading.value = false }
}
async function onFinalize() {
  if (!report.value) return
  if (!finalContent.value) { ElMessage.warning('定稿内容不能为空'); return }
  finLoading.value = true
  try {
    report.value = await finalizeReport(report.value.id, finalContent.value)
    ElMessage.success('已定稿')
  } catch (e) {} finally { finLoading.value = false }
}
function goIep() {
  router.push({ path: '/iep', query: { reportId: report.value.id } })
}
async function onDownload() {
  try {
    const blob = await downloadReportPdf(report.value.id)
    saveBlob(blob, `report-${report.value.id}.pdf`)
  } catch (e) {}
}
</script>
