<template>
  <view class="page">
    <view class="card add">
      <text class="title">添加成长记录</text>
      <picker :range="typeRange" :value="typeIndex" @change="onPickType">
        <view class="picker">类型:{{ typeRange[typeIndex] }} ▾</view>
      </picker>
      <textarea class="ta" v-model="content" placeholder="记录孩子的表现/进展" />
      <button class="btn" :loading="saving" @click="onAdd">保存</button>
    </view>

    <view v-for="log in logs" :key="log.id" class="card log">
      <view class="l-head">
        <text class="l-type">{{ log.logTypeLabel || log.logType }}</text>
        <text class="l-time">{{ log.createdAt }}</text>
      </view>
      <text class="l-content">{{ log.content }}</text>
    </view>
    <view v-if="!logs.length && !loading" class="empty"><text>暂无记录</text></view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { listLogs, createLog } from '../../api/log'

const LOG_TYPES = [
  { value: 'OBSERVATION', label: '日常观察' },
  { value: 'MILESTONE', label: '里程碑' },
  { value: 'CONCERN', label: '需关注' },
]
const typeRange = LOG_TYPES.map((t) => t.label)
const typeIndex = ref(0)
const childId = ref(null)
const content = ref('')
const logs = ref([])
const loading = ref(false)
const saving = ref(false)

onLoad((opts) => { childId.value = opts.childId })
onShow(() => { if (childId.value) load() })

function onPickType(e) { typeIndex.value = Number(e.detail.value) }

async function load() {
  loading.value = true
  try { logs.value = await listLogs(childId.value) }
  catch (e) { /* toast */ } finally { loading.value = false }
}

async function onAdd() {
  if (!content.value.trim()) {
    uni.showToast({ title: '请输入记录内容', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await createLog(childId.value, LOG_TYPES[typeIndex.value].value, content.value)
    content.value = ''
    uni.showToast({ title: '已保存', icon: 'success' })
    load()
  } catch (e) { /* toast */ } finally { saving.value = false }
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.title { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 16rpx; }
.picker { font-size: 30rpx; color: #3b82f6; margin-bottom: 16rpx; }
.ta { width: 100%; min-height: 120rpx; background: #f7f8fa; border-radius: 10rpx; padding: 20rpx; font-size: 28rpx; box-sizing: border-box; }
.btn { background: #3b82f6; color: #fff; border-radius: 12rpx; margin-top: 20rpx; font-size: 30rpx; }
.l-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10rpx; }
.l-type { font-size: 26rpx; color: #3b82f6; background: #eff6ff; padding: 4rpx 14rpx; border-radius: 16rpx; }
.l-time { font-size: 24rpx; color: #aaa; }
.l-content { font-size: 30rpx; line-height: 1.6; }
.empty { text-align: center; color: #bbb; margin-top: 120rpx; }
</style>
