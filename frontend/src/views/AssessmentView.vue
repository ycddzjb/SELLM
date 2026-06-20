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
      </el-form-item>

      <template v-if="items.length">
        <el-divider>量表作答</el-divider>
        <el-form-item v-for="item in items" :key="item.itemId" :label="item.stem">
          <el-rate v-model="answers[item.itemId]" :max="item.maxScore || 4" show-score />
        </el-form-item>
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
import { disorderLabel } from '../api/meta'

const route = useRoute()
const router = useRouter()
const childId = ref(route.query.childId || '')

const scales = ref([])
const scaleId = ref('')
const items = ref([])
const answers = reactive({})
const loading = ref(false)
const result = ref(null)

async function loadScales() {
  try { scales.value = await listScales() } catch (e) {}
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

onMounted(loadScales)
</script>
