<template>
  <view class="page" v-if="child">
    <view class="card">
      <view class="head">
        <text class="name">{{ child.name }}</text>
        <text class="tag">{{ disorderLabel(child.disorderType) }}</text>
      </view>
      <view class="row"><text class="k">基线</text><text class="v">{{ child.baselineSummary || '—' }}</text></view>
      <view class="row"><text class="k">年度目标</text><text class="v">{{ child.annualIepSummary || '—' }}</text></view>
      <view class="row"><text class="k">月目标</text><text class="v">{{ child.monthlyGoal || '—' }}</text></view>
      <view class="row"><text class="k">干预进展</text><text class="v">{{ child.interventionProgress || '—' }}</text></view>
      <view class="row"><text class="k">复评日期</text><text class="v">{{ child.reassessDate || '—' }}</text></view>
      <view class="row"><text class="k">IEP 到期</text><text class="v">{{ child.iepDueDate || '—' }}</text></view>
    </view>

    <view class="actions">
      <button class="act" @click="go('family-iep/list')">家庭 IEP</button>
      <button class="act" @click="go('report/list')">评估报告</button>
      <button class="act" @click="go('log/log')">成长记录</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getChild } from '../../api/child'
import { disorderLabel } from '../../utils/format'

const child = ref(null)
const childId = ref(null)

onLoad((opts) => {
  childId.value = opts.id
  load()
})

async function load() {
  try {
    child.value = await getChild(childId.value)
  } catch (e) { /* toast handled */ }
}

function go(page) {
  uni.navigateTo({ url: `/pages/${page}?childId=${childId.value}` })
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 28rpx; margin-bottom: 28rpx; }
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }
.name { font-size: 38rpx; font-weight: bold; }
.tag { background: #eff6ff; color: #3b82f6; font-size: 24rpx; padding: 6rpx 16rpx; border-radius: 20rpx; }
.row { display: flex; padding: 16rpx 0; border-top: 1rpx solid #f0f0f0; }
.k { width: 160rpx; color: #999; font-size: 28rpx; }
.v { flex: 1; font-size: 28rpx; }
.actions { display: flex; flex-direction: column; }
.act { background: #fff; color: #3b82f6; border-radius: 12rpx; margin-bottom: 20rpx; font-size: 30rpx; border: 1rpx solid #dbeafe; }
</style>
