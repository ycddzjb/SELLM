<template>
  <div>
    <h3>智能科研 · 课题书与信效度</h3>
    <el-tabs v-model="tab">
      <!-- 课题申报书 -->
      <el-tab-pane label="课题申报书" name="proposal">
        <el-card style="max-width:860px;margin-bottom:16px">
          <el-input v-model="topic" type="textarea" :rows="3" placeholder="课题主题" />
          <el-input v-model="proSubjects" placeholder="脱敏屏蔽名(逗号分隔,可空)" style="margin-top:8px" />
          <div style="margin-top:12px">
            <el-button type="primary" :loading="proGen" @click="onGenProposal">生成课题书草案</el-button>
            <el-button @click="loadProposals">刷新列表</el-button>
          </div>
        </el-card>
        <el-table :data="proposals" style="max-width:860px;margin-bottom:16px" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="topic" label="主题" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }"><el-tag :type="row.status==='FINALIZED'?'success':'info'">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }"><el-button link type="primary" @click="openProposal(row)">查看/编辑</el-button></template>
          </el-table-column>
        </el-table>
        <el-card v-if="proposal" style="max-width:860px">
          <div style="display:flex;justify-content:space-between"><span>课题书 #{{ proposal.id }}</span>
            <el-tag :type="proposal.status==='FINALIZED'?'success':'info'">{{ proposal.status }}</el-tag></div>
          <el-divider>AI 草案</el-divider>
          <pre style="white-space:pre-wrap;background:#f7f7f7;padding:12px;border-radius:4px">{{ proposal.aiDraft }}</pre>
          <el-divider>定稿内容</el-divider>
          <el-input v-model="proContent" type="textarea" :rows="6" :disabled="proposal.status==='FINALIZED'" />
          <div style="margin-top:12px">
            <el-button v-if="proposal.status!=='FINALIZED'" :loading="proEdit" @click="onEditProposal">保存</el-button>
            <el-button v-if="proposal.status!=='FINALIZED'" type="primary" :loading="proFin" @click="onFinProposal">定稿</el-button>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 信效度 -->
      <el-tab-pane label="信效度计算" name="reliability">
        <el-card style="max-width:860px">
          <p style="color:#888">每行一个被试,逗号分隔各题得分(纯数字矩阵)。例:<code>4,3,2</code> 换行 <code>3,3,2</code></p>
          <el-input v-model="scoresRaw" type="textarea" :rows="6" placeholder="4,3,2&#10;3,3,2&#10;2,4,3" />
          <div style="margin-top:12px"><el-button type="primary" :loading="relLoading" @click="onCompute">计算 Cronbach α</el-button></div>
          <el-descriptions v-if="reliability" :column="1" border style="margin-top:16px">
            <el-descriptions-item label="Cronbach α">{{ reliability.cronbachAlpha }}</el-descriptions-item>
            <el-descriptions-item label="分半信度">{{ reliability.splitHalf }}</el-descriptions-item>
            <el-descriptions-item label="项总相关">{{ JSON.stringify(reliability.itemTotalCorrelations) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  generateProposal, editProposal, finalizeProposal, getProposal, listProposals,
  computeReliability
} from '../api/research'

const tab = ref('proposal')
const topic = ref('')
const proSubjects = ref('')
const proposals = ref([])
const proposal = ref(null)
const proContent = ref('')
const proGen = ref(false), proEdit = ref(false), proFin = ref(false)
const scoresRaw = ref('')
const reliability = ref(null)
const relLoading = ref(false)

onMounted(loadProposals)

function splitNames(raw) { return (raw || '').split(/[,，]/).map(s => s.trim()).filter(Boolean) }

async function loadProposals() { try { proposals.value = await listProposals() } catch (e) {} }

async function onGenProposal() {
  if (!topic.value.trim()) { ElMessage.warning('请填课题主题'); return }
  proGen.value = true
  try {
    proposal.value = await generateProposal({ topic: topic.value, subjectNames: splitNames(proSubjects.value) })
    proContent.value = proposal.value.content || proposal.value.aiDraft
    ElMessage.success('已生成课题书草案'); loadProposals()
  } catch (e) {} finally { proGen.value = false }
}
async function openProposal(row) {
  try { proposal.value = await getProposal(row.id); proContent.value = proposal.value.content || proposal.value.aiDraft } catch (e) {}
}
async function onEditProposal() {
  proEdit.value = true
  try { proposal.value = await editProposal(proposal.value.id, proContent.value); ElMessage.success('已保存') } catch (e) {} finally { proEdit.value = false }
}
async function onFinProposal() {
  proFin.value = true
  try { proposal.value = await finalizeProposal(proposal.value.id); ElMessage.success('已定稿'); loadProposals() } catch (e) {} finally { proFin.value = false }
}

async function onCompute() {
  const scores = scoresRaw.value.trim().split(/\n/).map(line => line.split(/[,，]/).map(s => Number(s.trim())).filter(n => !isNaN(n))).filter(r => r.length)
  if (!scores.length) { ElMessage.warning('请输入分数矩阵'); return }
  relLoading.value = true
  try { reliability.value = await computeReliability({ scores }); ElMessage.success('计算完成') } catch (e) {} finally { relLoading.value = false }
}
</script>
