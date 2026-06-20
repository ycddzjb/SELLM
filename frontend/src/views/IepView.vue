<template>
  <div>
    <h3>个别化教育计划(IEP)</h3>
    <el-form label-width="120px" style="max-width:760px">
      <el-form-item label="报告ID">
        <el-input v-model="reportId" placeholder="从报告页带入,或手填">
          <template #append>
            <el-button :loading="genLoading" @click="onGenerate">生成 IEP 草案</el-button>
          </template>
        </el-input>
      </el-form-item>
    </el-form>

    <el-card v-if="iep" style="margin-top:16px;max-width:760px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>IEP #{{ iep.id }}</span>
        <el-tag :type="iep.status === 'FINALIZED' ? 'success' : 'info'">{{ iep.status }}</el-tag>
      </div>
      <el-divider>AI 草案(仅供参考,需人工把关)</el-divider>
      <pre style="white-space:pre-wrap;background:#f7f7f7;padding:12px;border-radius:4px">{{ iep.draft }}</pre>
      <el-divider>定稿内容</el-divider>
      <el-input v-model="finalContent" type="textarea" :rows="6"
                placeholder="在 AI 草案基础上修改为最终 IEP" />
      <div style="margin-top:12px">
        <el-button type="primary" :loading="finLoading" @click="onFinalize">定稿</el-button>
        <el-button v-if="iep.status === 'FINALIZED'" @click="onDownload">下载 PDF</el-button>
      </div>
      <p v-if="iep.finalizedContent" style="margin-top:12px;color:#666">
        已定稿内容:{{ iep.finalizedContent }}
      </p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { generateIep, getIep, finalizeIep, downloadIepPdf } from '../api/ieps'
import { saveBlob } from '../api/familyIeps'

const route = useRoute()
const reportId = ref(route.query.reportId || '')
const iep = ref(null)
const finalContent = ref('')
const genLoading = ref(false)
const finLoading = ref(false)

onMounted(() => {
  if (route.query.iepId) {
    loadIep(Number(route.query.iepId))
  }
})

async function loadIep(iepId) {
  genLoading.value = true
  try {
    iep.value = await getIep(iepId)
    finalContent.value = iep.value.finalizedContent || iep.value.draft
  } catch (e) {} finally { genLoading.value = false }
}

async function onGenerate() {
  if (!reportId.value) { ElMessage.warning('请填写报告ID'); return }
  genLoading.value = true
  try {
    iep.value = await generateIep(Number(reportId.value))
    finalContent.value = iep.value.draft
    ElMessage.success('已生成草案')
  } catch (e) {} finally { genLoading.value = false }
}
async function onFinalize() {
  if (!iep.value) return
  if (!finalContent.value) { ElMessage.warning('定稿内容不能为空'); return }
  finLoading.value = true
  try {
    iep.value = await finalizeIep(iep.value.id, finalContent.value)
    ElMessage.success('已定稿')
  } catch (e) {} finally { finLoading.value = false }
}
async function onDownload() {
  try {
    const blob = await downloadIepPdf(iep.value.id)
    saveBlob(blob, `iep-${iep.value.id}.pdf`)
  } catch (e) {}
}
</script>
