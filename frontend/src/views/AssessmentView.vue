<template>
  <div>
    <h3>评估(CARS 量表)</h3>
    <el-form label-width="120px" style="max-width:640px">
      <el-form-item label="儿童ID">
        <el-input v-model="childId" placeholder="从儿童档案页点'评估'带入,或手填" />
      </el-form-item>
      <el-divider>量表作答(0-4 分)</el-divider>
      <el-form-item v-for="item in items" :key="item.itemId" :label="item.stem">
        <el-rate v-model="answers[item.itemId]" :max="4" show-score />
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="onSubmit">提交评估</el-button>
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
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { submitAssessment } from '../api/assessments'

// 第一版硬编码 CARS 题目(与后端种子 scaleId=cars 的 q1/q2 一致)
const items = [
  { itemId: 'q1', stem: '与人交往' },
  { itemId: 'q2', stem: '语言沟通' }
]
const route = useRoute()
const router = useRouter()
const childId = ref(route.query.childId || '')
const answers = reactive({ q1: 0, q2: 0 })
const loading = ref(false)
const result = ref(null)

async function onSubmit() {
  if (!childId.value) {
    ElMessage.warning('请填写儿童ID')
    return
  }
  loading.value = true
  try {
    const payload = {
      childId: Number(childId.value),
      scaleId: 'cars',
      answers: items.map((it) => ({ itemId: it.itemId, score: answers[it.itemId] }))
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
</script>
