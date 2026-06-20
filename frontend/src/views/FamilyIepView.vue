<template>
  <div>
    <h3>家庭 IEP(家长)</h3>
    <el-form label-width="100px" style="max-width:760px">
      <el-form-item label="选择孩子">
        <el-select v-model="childId" placeholder="选择您的孩子" style="width:100%">
          <el-option v-for="c in children" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="家庭目标">
        <el-input v-model="parentGoal" type="textarea" :rows="2"
                  placeholder="您希望在家帮助孩子达成的目标,如:提升居家社交、改善作息" />
      </el-form-item>
      <el-button type="primary" :loading="genLoading" @click="onGenerate">生成家庭 IEP</el-button>
      <p style="color:#999;font-size:12px;margin-top:8px">
        需该孩子已有定稿评估报告;系统会据最新报告与您的目标生成家庭训练计划草案。
      </p>
    </el-form>

    <el-card v-if="familyIep" style="margin-top:16px;max-width:760px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>家庭 IEP #{{ familyIep.id }}</span>
        <el-tag :type="familyIep.status === 'FINALIZED' ? 'success' : 'info'">{{ familyIep.status }}</el-tag>
      </div>
      <el-divider>AI 草案(仅供参考,请按家庭实际调整)</el-divider>
      <pre style="white-space:pre-wrap;background:#f7f7f7;padding:12px;border-radius:4px">{{ familyIep.draft }}</pre>
      <el-divider>定稿内容</el-divider>
      <el-input v-model="finalContent" type="textarea" :rows="6" placeholder="在草案基础上修改为家庭最终计划" />
      <div style="margin-top:12px">
        <el-button type="primary" :loading="finLoading" @click="onFinalize">定稿</el-button>
        <el-button v-if="familyIep.status === 'FINALIZED'" @click="onDownload">下载 PDF</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listChildren } from '../api/children'
import {
  generateFamilyIep, finalizeFamilyIep, downloadFamilyIepPdf, saveBlob
} from '../api/familyIeps'

const children = ref([])
const childId = ref(null)
const parentGoal = ref('')
const familyIep = ref(null)
const finalContent = ref('')
const genLoading = ref(false)
const finLoading = ref(false)

onMounted(async () => {
  try { children.value = await listChildren() } catch (e) {}
})

async function onGenerate() {
  if (!childId.value) { ElMessage.warning('请选择孩子'); return }
  if (!parentGoal.value) { ElMessage.warning('请填写家庭目标'); return }
  genLoading.value = true
  try {
    familyIep.value = await generateFamilyIep(childId.value, parentGoal.value)
    finalContent.value = familyIep.value.draft
    ElMessage.success('已生成草案')
  } catch (e) {} finally { genLoading.value = false }
}
async function onFinalize() {
  if (!familyIep.value || !finalContent.value) { ElMessage.warning('定稿内容不能为空'); return }
  finLoading.value = true
  try {
    familyIep.value = await finalizeFamilyIep(familyIep.value.id, finalContent.value)
    ElMessage.success('已定稿')
  } catch (e) {} finally { finLoading.value = false }
}
async function onDownload() {
  try {
    const blob = await downloadFamilyIepPdf(familyIep.value.id)
    saveBlob(blob, `family-iep-${familyIep.value.id}.pdf`)
  } catch (e) {}
}
</script>
