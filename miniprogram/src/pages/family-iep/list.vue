<template>
  <view class="page">
    <view class="gen card">
      <text class="title">生成家庭 IEP 草案</text>
      <textarea class="ta" v-model="parentGoal" placeholder="填写您对孩子的期望/家庭目标" />
      <button class="btn" :loading="generating" @click="onGenerate">生成草案</button>
    </view>

    <view v-for="item in list" :key="item.id" class="card item" @click="goDetail(item.id)">
      <view class="i-head">
        <text class="i-goal">{{ item.parentGoal || '家庭 IEP' }}</text>
        <text class="i-status" :class="item.status">{{ statusLabel(item.status) }}</text>
      </view>
    </view>
    <view v-if="!list.length && !loading" class="empty"><text>暂无家庭 IEP</text></view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { listFamilyIeps, generateFamilyIep } from '../../api/iep'
import { statusLabel } from '../../utils/format'

const childId = ref(null)
const parentGoal = ref('')
const list = ref([])
const loading = ref(false)
const generating = ref(false)

onLoad((opts) => { childId.value = opts.childId })
onShow(() => { if (childId.value) load() })

async function load() {
  loading.value = true
  try { list.value = await listFamilyIeps(childId.value) }
  catch (e) { /* toast */ } finally { loading.value = false }
}

async function onGenerate() {
  if (!parentGoal.value.trim()) {
    uni.showToast({ title: '请填写家庭目标', icon: 'none' })
    return
  }
  generating.value = true
  try {
    const r = await generateFamilyIep(childId.value, parentGoal.value)
    parentGoal.value = ''
    uni.navigateTo({ url: `/pages/family-iep/detail?id=${r.id}` })
  } catch (e) { /* toast */ } finally { generating.value = false }
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/family-iep/detail?id=${id}` })
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 28rpx; margin-bottom: 20rpx; }
.gen .title { font-size: 30rpx; font-weight: bold; display: block; margin-bottom: 16rpx; }
.ta { width: 100%; min-height: 140rpx; background: #f7f8fa; border-radius: 10rpx; padding: 20rpx; font-size: 28rpx; box-sizing: border-box; }
.btn { background: #3b82f6; color: #fff; border-radius: 12rpx; margin-top: 20rpx; font-size: 30rpx; }
.item .i-head { display: flex; justify-content: space-between; align-items: center; }
.i-goal { font-size: 30rpx; flex: 1; }
.i-status { font-size: 24rpx; padding: 4rpx 16rpx; border-radius: 20rpx; background: #f3f4f6; color: #666; }
.i-status.FINALIZED { background: #dcfce7; color: #16a34a; }
.i-status.DRAFT { background: #fef9c3; color: #ca8a04; }
.empty { text-align: center; color: #bbb; margin-top: 120rpx; }
</style>
