<template>
  <view class="page">
    <view v-for="item in list" :key="item.id" class="card item" @click="goDetail(item.id)">
      <view class="i-head">
        <text class="i-title">评估报告 #{{ item.id }}</text>
        <text class="i-status" :class="item.status">{{ statusLabel(item.status) }}</text>
      </view>
    </view>
    <view v-if="!list.length && !loading" class="empty"><text>暂无评估报告</text></view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { listReports } from '../../api/report'
import { statusLabel } from '../../utils/format'

const childId = ref(null)
const list = ref([])
const loading = ref(false)

onLoad((opts) => { childId.value = opts.childId })
onShow(() => { if (childId.value) load() })

async function load() {
  loading.value = true
  try { list.value = await listReports(childId.value) }
  catch (e) { /* toast */ } finally { loading.value = false }
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/report/detail?id=${id}` })
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 28rpx; margin-bottom: 20rpx; }
.i-head { display: flex; justify-content: space-between; align-items: center; }
.i-title { font-size: 30rpx; }
.i-status { font-size: 24rpx; padding: 4rpx 16rpx; border-radius: 20rpx; background: #f3f4f6; color: #666; }
.i-status.FINALIZED { background: #dcfce7; color: #16a34a; }
.i-status.DRAFT { background: #fef9c3; color: #ca8a04; }
.empty { text-align: center; color: #bbb; margin-top: 120rpx; }
</style>
