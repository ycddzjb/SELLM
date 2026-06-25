<template>
  <div>
    <h3>教学训练</h3>
    <el-tabs v-model="mainTab">
      <el-tab-pane label="训练方案" name="train">
    <p style="color:#888;margin-top:-6px">据定稿 IEP 生成教案草案,人工定稿后可生成课件。AI 仅产草案,需人工把关。</p>

    <!-- 生成教案 -->
    <el-card style="max-width:860px;margin-bottom:16px">
      <el-form label-width="110px">
        <el-form-item label="IEP 内容">
          <el-input v-model="form.iepContent" type="textarea" :rows="4"
                    placeholder="粘贴定稿 IEP 正文(教案据此生成)" />
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="form.scene" placeholder="场景">
            <el-option label="家庭" value="HOME" />
            <el-option label="学校" value="SCHOOL" />
            <el-option label="机构" value="CENTER" />
          </el-select>
        </el-form-item>
        <el-form-item label="教学模式">
          <el-select v-model="form.mode" placeholder="模式">
            <el-option label="一对一" value="ONE_ON_ONE" />
            <el-option label="小组" value="GROUP" />
          </el-select>
        </el-form-item>
        <el-form-item label="障碍类型">
          <el-select v-model="form.disorderType" placeholder="障碍类型">
            <el-option v-for="d in disorderOptions" :key="d.code" :label="d.label" :value="d.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="脱敏屏蔽名">
          <el-input v-model="form.subjectNamesRaw" placeholder="逗号分隔:儿童/家长/校名(出网脱敏用,可空)" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="genLoading" @click="onGenerate">生成教案草案</el-button>
          <el-button @click="loadPlans">刷新教案列表</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 教案列表 -->
    <el-table :data="plans" style="max-width:860px;margin-bottom:16px" size="small">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'FINALIZED' ? 'success' : 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="{ row }">
          <el-button link type="primary" @click="openPlan(row)">查看/编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- PLACEHOLDER_DETAIL -->
    <el-card v-if="plan" style="max-width:860px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>教案 #{{ plan.id }}</span>
        <el-tag :type="plan.status === 'FINALIZED' ? 'success' : 'info'">{{ plan.status }}</el-tag>
      </div>
      <el-divider>AI 草案</el-divider>
      <pre style="white-space:pre-wrap;background:#f7f7f7;padding:12px;border-radius:4px">{{ plan.aiDraft }}</pre>
      <el-divider>定稿内容</el-divider>
      <el-input v-model="planContent" type="textarea" :rows="6"
                :disabled="plan.status === 'FINALIZED'" placeholder="在草案基础上编辑" />
      <div style="margin-top:12px">
        <el-button v-if="plan.status !== 'FINALIZED'" :loading="editLoading" @click="onEditPlan">保存编辑</el-button>
        <el-button v-if="plan.status !== 'FINALIZED'" type="primary" :loading="finLoading" @click="onFinalizePlan">定稿</el-button>
        <el-button v-if="plan.status === 'FINALIZED'" type="success" :loading="cwLoading" @click="onGenCourseware">生成课件</el-button>
      </div>

      <template v-if="courseware">
        <el-divider>课件 #{{ courseware.id }}({{ courseware.status }})</el-divider>
        <pre style="white-space:pre-wrap;background:#f0f9eb;padding:12px;border-radius:4px">{{ courseware.content }}</pre>
        <el-button v-if="courseware.status !== 'FINALIZED'" type="primary" :loading="cwFinLoading"
                   @click="onFinalizeCw">课件定稿</el-button>
      </template>
    </el-card>
      </el-tab-pane>

      <el-tab-pane label="教案" name="lesson">
        <TeachingContentPanel type="LESSON" />
      </el-tab-pane>
      <el-tab-pane label="课件" name="courseware">
        <TeachingContentPanel type="COURSEWARE" />
      </el-tab-pane>
      <el-tab-pane label="案例" name="case">
        <TeachingContentPanel type="CASE" />
      </el-tab-pane>
      <el-tab-pane label="习题" name="exercise">
        <TeachingContentPanel type="EXERCISE" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>


<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { DISORDER_TYPES } from '../api/meta'
import {
  generatePlan, editPlan, finalizePlan, getPlan, listPlans,
  generateCourseware, finalizeCourseware
} from '../api/teaching'
import TeachingContentPanel from '../components/TeachingContentPanel.vue'

const mainTab = ref('train')
const disorderOptions = DISORDER_TYPES
const form = ref({ iepContent: '', scene: 'SCHOOL', mode: 'ONE_ON_ONE', disorderType: 'ASD', subjectNamesRaw: '' })
const plans = ref([])
const plan = ref(null)
const planContent = ref('')
const courseware = ref(null)
const genLoading = ref(false)
const editLoading = ref(false)
const finLoading = ref(false)
const cwLoading = ref(false)
const cwFinLoading = ref(false)

onMounted(loadPlans)

async function loadPlans() {
  try { plans.value = await listPlans() } catch (e) {}
}

function splitNames(raw) {
  return (raw || '').split(/[,，]/).map(s => s.trim()).filter(Boolean)
}

async function onGenerate() {
  if (!form.value.iepContent.trim()) { ElMessage.warning('请填写 IEP 内容'); return }
  genLoading.value = true
  try {
    plan.value = await generatePlan({
      iepContent: form.value.iepContent,
      scene: form.value.scene,
      mode: form.value.mode,
      disorderType: form.value.disorderType,
      subjectNames: splitNames(form.value.subjectNamesRaw)
    })
    planContent.value = plan.value.content || plan.value.aiDraft
    courseware.value = null
    ElMessage.success('已生成教案草案')
    loadPlans()
  } catch (e) {} finally { genLoading.value = false }
}

async function openPlan(row) {
  try {
    plan.value = await getPlan(row.id)
    planContent.value = plan.value.content || plan.value.aiDraft
    courseware.value = null
  } catch (e) {}
}

async function onEditPlan() {
  editLoading.value = true
  try {
    plan.value = await editPlan(plan.value.id, planContent.value)
    ElMessage.success('已保存')
  } catch (e) {} finally { editLoading.value = false }
}

async function onFinalizePlan() {
  finLoading.value = true
  try {
    plan.value = await finalizePlan(plan.value.id)
    ElMessage.success('教案已定稿')
    loadPlans()
  } catch (e) {} finally { finLoading.value = false }
}

async function onGenCourseware() {
  cwLoading.value = true
  try {
    courseware.value = await generateCourseware({
      lessonPlanId: plan.value.id,
      format: 'TEXT',
      subjectNames: splitNames(form.value.subjectNamesRaw)
    })
    ElMessage.success('已生成课件草案')
  } catch (e) {} finally { cwLoading.value = false }
}

async function onFinalizeCw() {
  cwFinLoading.value = true
  try {
    courseware.value = await finalizeCourseware(courseware.value.id)
    ElMessage.success('课件已定稿')
  } catch (e) {} finally { cwFinLoading.value = false }
}
</script>
