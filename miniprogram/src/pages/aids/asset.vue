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
        <image v-if="isImage && taskPreview" class="preview" :src="taskPreview" mode="widthFix" @tap="previewFull(taskPreview)" />
        <text v-else-if="isImage" class="t-key">加载预览中…</text>
        <text v-else class="t-key">产物:{{ task.result && task.result.storageKey }}</text>
      </view>
      <text v-else-if="task.status === 'FAILED'" class="t-err">{{ task.error }}</text>
    </view>

    <!-- 我的素材 -->
    <view class="card" v-if="assets.length">
      <text class="title">我的素材</text>
      <view v-for="a in assets" :key="a.id" class="asset-item" @tap="openAsset(a)">
        <view class="asset-row">
          <text class="ar-type">{{ typeLabel(a.type) }} #{{ a.id }}</text>
          <text class="ar-status" :class="a.status">{{ statusLabel(a.status) }} ›</text>
        </view>
        <image
          v-if="expandedId === a.id && previews[a.id]"
          class="preview" :src="previews[a.id]" mode="widthFix"
          @tap.stop="previewFull(previews[a.id])" />
        <text v-else-if="expandedId === a.id && loadingId === a.id" class="t-key">加载中…</text>
        <text v-else-if="expandedId === a.id && a.status === 'FAILED'" class="t-err">生成失败,无产物</text>
        <text v-else-if="expandedId === a.id" class="t-key">该素材无可预览图片</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import { submitAsset, pollAssetTask, listAssets, fetchAssetImageUrl } from '../../api/aids'
import { statusLabel, ASSET_TYPE_OPTIONS } from '../../utils/format'
import { useUserStore } from '../../store/user'

const userStore = useUserStore()
const typeRange = ASSET_TYPE_OPTIONS.map((o) => o.label)
const typeIndex = ref(0)
const prompt = ref('')
const submitting = ref(false)
const task = ref(null)
const assets = ref([])
const taskPreview = ref('')          // 当前任务图片 data URL
const previews = ref({})             // 历史素材 id → data URL 缓存
const expandedId = ref(null)         // 当前展开的素材 id
const loadingId = ref(null)          // 正在取图的素材 id
let timer = null

const isImage = computed(() =>
  task.value && task.value.result && (task.value.result.mimeType || '').startsWith('image/'))

// 当前任务成功且为图片时,带 token 拉取并转 data URL(image 标签不带鉴权头)
watch(() => task.value && task.value.status, async (st) => {
  if (st === 'SUCCESS' && isImage.value) {
    try {
      taskPreview.value = await fetchAssetImageUrl(task.value.taskId)
    } catch (e) { taskPreview.value = '' }
  } else if (st !== 'SUCCESS') {
    taskPreview.value = ''
  }
})

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
    taskPreview.value = ''
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

// 点击历史素材:展开/收起,首次展开时带 token 拉图
async function openAsset(a) {
  if (expandedId.value === a.id) { expandedId.value = null; return }
  expandedId.value = a.id
  const isImg = (a.mimeType || '').startsWith('image/')
    || a.status === 'SUCCESS'   // 旧记录可能无 mimeType 字段,SUCCESS 先试图
  if (a.status === 'SUCCESS' && isImg && !previews.value[a.id]) {
    loadingId.value = a.id
    try {
      previews.value[a.id] = await fetchAssetImageUrl(a.id)
    } catch (e) { /* 非图片或取失败,模板显示提示 */ } finally { loadingId.value = null }
  }
}

// 全屏预览
function previewFull(url) {
  if (url) uni.previewImage({ urls: [url] })
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
.asset-item { border-top: 1rpx solid #f0f0f0; }
.asset-row { display: flex; justify-content: space-between; padding: 16rpx 0; }
.ar-type { font-size: 28rpx; }
.ar-status { font-size: 24rpx; color: #666; }
.ar-status.SUCCESS { color: #16a34a; }
.ar-status.FAILED { color: #dc2626; }
</style>
