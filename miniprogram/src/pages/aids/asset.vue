<template>
  <view class="page">
    <view class="card">
      <text class="title">生成教学素材</text>
      <picker :range="typeRange" :value="typeIndex" @change="onPickType">
        <view class="picker">类型:{{ typeRange[typeIndex] }} ▾</view>
      </picker>
      <textarea class="ta" v-model="prompt" placeholder="描述你想要的素材,如「为自闭症儿童设计认识情绪的绘本」" />
      <button class="btn" :loading="submitting" @click="onSubmit">提交生成</button>
    </view>

    <!-- 当前任务状态 -->
    <view v-if="task" class="card">
      <view class="t-head">
        <text class="t-title">当前任务 #{{ task.taskId }}</text>
        <text class="t-status" :class="task.status">{{ statusLabel(task.status) }}</text>
      </view>
      <view v-if="task.status === 'PENDING' || task.status === 'RUNNING'" class="t-loading">
        <text>生成中,请稍候…</text>
      </view>
      <view v-else-if="task.status === 'SUCCESS'">
        <image v-if="isImage" class="preview" :src="previewUrl" mode="widthFix" />
        <text v-else class="t-key">产物:{{ task.result && task.result.storageKey }}</text>
      </view>
      <text v-else-if="task.status === 'FAILED'" class="t-err">{{ task.error }}</text>
    </view>

    <!-- 我的素材 -->
    <view class="card" v-if="assets.length">
      <text class="title">我的素材</text>
      <view v-for="a in assets" :key="a.id" class="asset-row">
        <text class="ar-type">{{ typeLabel(a.type) }}</text>
        <text class="ar-status" :class="a.status">{{ statusLabel(a.status) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import { submitAsset, pollAssetTask, listAssets } from '../../api/aids'
import { statusLabel, ASSET_TYPE_OPTIONS } from '../../utils/format'
import { useUserStore } from '../../store/user'

const userStore = useUserStore()
const BASE_URL = 'http://localhost:8888'
const typeRange = ASSET_TYPE_OPTIONS.map((o) => o.label)
const typeIndex = ref(0)
const prompt = ref('')
const submitting = ref(false)
const task = ref(null)
const assets = ref([])
let timer = null

const isImage = computed(() =>
  task.value && task.value.result && (task.value.result.mimeType || '').startsWith('image/'))
const previewUrl = computed(() =>
  task.value ? `${BASE_URL}/api/aids/assets/${task.value.taskId}/raw` : '')

onShow(() => {
  if (!userStore.token) { uni.reLaunch({ url: '/pages/login/login' }); return }
  loadAssets()
})
onUnload(() => stopPoll())

function onPickType(e) { typeIndex.value = Number(e.detail.value) }
function typeLabel(t) {
  const o = ASSET_TYPE_OPTIONS.find((x) => x.value === t)
  return o ? o.label : t
}

async function onSubmit() {
  if (!prompt.value.trim()) {
    uni.showToast({ title: '请输入素材描述', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const r = await submitAsset(ASSET_TYPE_OPTIONS[typeIndex.value].value, prompt.value)
    task.value = { taskId: r.taskId, status: 'PENDING' }
    prompt.value = ''
    startPoll(r.taskId)
  } catch (e) { /* toast */ } finally { submitting.value = false }
}

function startPoll(taskId) {
  stopPoll()
  timer = setInterval(async () => {
    try {
      const s = await pollAssetTask(taskId)
      task.value = { taskId, ...s }
      if (s.status === 'SUCCESS' || s.status === 'FAILED') {
        stopPoll()
        loadAssets()
      }
    } catch (e) { stopPoll() }
  }, 1500)
}

function stopPoll() {
  if (timer) { clearInterval(timer); timer = null }
}

async function loadAssets() {
  try { assets.value = await listAssets() } catch (e) { /* toast */ }
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.title { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 16rpx; }
.picker { font-size: 30rpx; color: #3b82f6; margin-bottom: 16rpx; }
.ta { width: 100%; min-height: 160rpx; background: #f7f8fa; border-radius: 10rpx; padding: 20rpx; font-size: 28rpx; box-sizing: border-box; }
.btn { background: #8b5cf6; color: #fff; border-radius: 12rpx; margin-top: 20rpx; font-size: 30rpx; }
.t-head { display: flex; justify-content: space-between; align-items: center; }
.t-title { font-size: 30rpx; }
.t-status { font-size: 24rpx; padding: 4rpx 16rpx; border-radius: 20rpx; background: #f3f4f6; color: #666; }
.t-status.SUCCESS { background: #dcfce7; color: #16a34a; }
.t-status.FAILED { background: #fee2e2; color: #dc2626; }
.t-loading { color: #888; font-size: 28rpx; margin-top: 16rpx; }
.t-err { color: #dc2626; font-size: 28rpx; margin-top: 16rpx; display: block; }
.t-key { font-size: 26rpx; color: #555; margin-top: 16rpx; display: block; }
.preview { width: 100%; margin-top: 16rpx; border-radius: 10rpx; }
.asset-row { display: flex; justify-content: space-between; padding: 16rpx 0; border-top: 1rpx solid #f0f0f0; }
.ar-type { font-size: 28rpx; }
.ar-status { font-size: 24rpx; color: #666; }
.ar-status.SUCCESS { color: #16a34a; }
.ar-status.FAILED { color: #dc2626; }
</style>
