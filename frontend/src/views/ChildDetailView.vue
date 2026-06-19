<template>
  <div v-loading="loading">
    <el-page-header @back="$router.push('/children')" :content="child ? child.name + ' 的档案' : '儿童档案'" />
    <el-descriptions v-if="child" :column="3" border style="margin:16px 0">
      <el-descriptions-item label="ID">{{ child.id }}</el-descriptions-item>
      <el-descriptions-item label="姓名">{{ child.name }}</el-descriptions-item>
      <el-descriptions-item label="障碍类型">{{ child.disorderType }}</el-descriptions-item>
    </el-descriptions>

    <div style="margin-bottom:12px">
      <el-button type="primary" @click="goAssessment">新建评估</el-button>
    </div>

    <el-divider>评估历史</el-divider>
    <el-table :data="assessments" border size="small">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="bandLabel" label="分段" />
      <el-table-column prop="totalScore" label="总分" width="90" />
      <el-table-column prop="interpretation" label="解读" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button size="small" @click="goReportFrom(row)">生成报告</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider>报告历史</el-divider>
    <el-table :data="reports" border size="small">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column prop="finalizedContent" label="定稿内容" show-overflow-tooltip />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" @click="openReport(row)">查看/定稿</el-button>
          <el-button size="small" @click="goIepFrom(row)">生成IEP</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider>IEP 历史</el-divider>
    <el-table :data="ieps" border size="small">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column prop="finalizedContent" label="定稿内容" show-overflow-tooltip />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button size="small" @click="openIep(row)">查看/定稿</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getChild } from '../api/children'
import { listAssessmentsByChild } from '../api/assessments'
import { listReportsByChild } from '../api/reports'
import { listIepsByChild } from '../api/ieps'

const route = useRoute()
const router = useRouter()
const childId = Number(route.params.id)
const child = ref(null)
const assessments = ref([])
const reports = ref([])
const ieps = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    child.value = await getChild(childId)
    assessments.value = await listAssessmentsByChild(childId)
    reports.value = await listReportsByChild(childId)
    ieps.value = await listIepsByChild(childId)
  } finally { loading.value = false }
}
onMounted(load)

function goAssessment() {
  router.push({ path: '/assessment', query: { childId } })
}
function goReportFrom(a) {
  router.push({ path: '/report', query: { assessmentId: a.id } })
}
function openReport(r) {
  router.push({ path: '/report', query: { reportId: r.id } })
}
function goIepFrom(r) {
  router.push({ path: '/iep', query: { reportId: r.id } })
}
function openIep(i) {
  router.push({ path: '/iep', query: { iepId: i.id } })
}
</script>
