<template>
  <view class="page" v-if="report">
    <view class="card">
      <view class="head">
        <text class="title">评估报告 #{{ report.id }}</text>
        <text class="status" :class="report.status">{{ statusLabel(report.status) }}</text>
      </view>
    </view>
    <view class="card">
      <text class="section">{{ report.status === 'FINALIZED' ? '定稿内容' : 'AI 草案' }}</text>
      <text class="content">{{ report.status === 'FINALIZED' ? report.finalizedContent : report.draft }}</text>
    </view>
    <button v-if="report.status === 'FINALIZED'" class="btn" :loading="downloading" @click="downloadPdf">
      下载 PDF
    </button>
    <view v-else class="tip"><text>报告定稿后方可下载 PDF</text></view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getReport } from '../../api/report'
import { statusLabel } from '../../utils/format'
import { useUserStore } from '../../store/user'

const report = ref(null)
const id = ref(null)
const downloading = ref(false)
const userStore = useUserStore()
const BASE_URL = 'http://localhost:8888'

onLoad((opts) => { id.value = opts.id; load() })

async function load() {
  try { report.value = await getReport(id.value) } catch (e) { /* toast */ }
}

function downloadPdf() {
  downloading.value = true
  uni.downloadFile({
    url: `${BASE_URL}/api/reports/${id.value}/pdf`,
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
.head { display: flex; justify-content: space-between; align-items: center; }
.title { font-size: 34rpx; font-weight: bold; }
.status { font-size: 24rpx; padding: 4rpx 16rpx; border-radius: 20rpx; background: #f3f4f6; color: #666; }
.status.FINALIZED { background: #dcfce7; color: #16a34a; }
.status.DRAFT { background: #fef9c3; color: #ca8a04; }
.section { font-size: 28rpx; color: #999; display: block; margin-bottom: 12rpx; }
.content { font-size: 30rpx; line-height: 1.7; white-space: pre-wrap; }
.btn { background: #3b82f6; color: #fff; border-radius: 12rpx; font-size: 32rpx; }
.tip { text-align: center; color: #bbb; font-size: 24rpx; margin-top: 20rpx; }
</style>
