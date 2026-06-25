<template>
  <view class="page" v-if="iep">
    <view class="card">
      <view class="head">
        <text class="title">家庭 IEP</text>
        <text class="status" :class="iep.status">{{ statusLabel(iep.status) }}</text>
      </view>
      <text class="goal">家庭目标:{{ iep.parentGoal || '—' }}</text>
    </view>

    <view class="card">
      <text class="section">{{ iep.status === 'FINALIZED' ? '定稿内容' : 'AI 草案' }}</text>
      <text class="content">{{ iep.status === 'FINALIZED' ? iep.finalizedContent : iep.draft }}</text>
    </view>

    <button v-if="iep.status === 'FINALIZED'" class="btn" :loading="downloading" @click="downloadPdf">
      下载 PDF
    </button>
    <view v-else class="tip">
      <text>草案需老师/家长在管理端定稿后方可下载 PDF</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getFamilyIep } from '../../api/iep'
import { statusLabel } from '../../utils/format'
import { useUserStore } from '../../store/user'

const iep = ref(null)
const id = ref(null)
const downloading = ref(false)
const userStore = useUserStore()
const BASE_URL = 'http://localhost:8888'

onLoad((opts) => { id.value = opts.id; load() })

async function load() {
  try { iep.value = await getFamilyIep(id.value) } catch (e) { /* toast */ }
}

function downloadPdf() {
  downloading.value = true
  uni.downloadFile({
    url: `${BASE_URL}/api/family-ieps/${id.value}/pdf`,
    header: { Authorization: `Bearer ${userStore.token}` },
    success: (res) => {
      if (res.statusCode === 200) {
        uni.openDocument({ filePath: res.tempFilePath, fileType: 'pdf' })
      } else {
        uni.showToast({ title: '下载失败', icon: 'none' })
      }
    },
    fail: () => uni.showToast({ title: '下载失败', icon: 'none' }),
    complete: () => { downloading.value = false },
  })
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 28rpx; margin-bottom: 20rpx; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; }
.title { font-size: 34rpx; font-weight: bold; }
.status { font-size: 24rpx; padding: 4rpx 16rpx; border-radius: 20rpx; background: #f3f4f6; color: #666; }
.status.FINALIZED { background: #dcfce7; color: #16a34a; }
.status.DRAFT { background: #fef9c3; color: #ca8a04; }
.goal { font-size: 28rpx; color: #555; }
.section { font-size: 28rpx; color: #999; display: block; margin-bottom: 12rpx; }
.content { font-size: 30rpx; line-height: 1.7; white-space: pre-wrap; }
.btn { background: #3b82f6; color: #fff; border-radius: 12rpx; font-size: 32rpx; }
.tip { text-align: center; color: #bbb; font-size: 24rpx; margin-top: 20rpx; }
</style>
