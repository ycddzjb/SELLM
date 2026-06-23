<template>
  <view class="page">
    <view class="filter card">
      <picker :range="typeRange" :value="typeIndex" @change="onPick">
        <view class="picker">障碍类型:{{ typeRange[typeIndex] }} ▾</view>
      </picker>
      <navigator url="/pages/aids/asset" class="asset-entry">文生素材 →</navigator>
    </view>

    <view v-for="aid in list" :key="aid.id" class="card aid">
      <view class="a-head">
        <text class="a-name">{{ aid.name }}</text>
        <text class="a-cat">{{ aid.category }}</text>
      </view>
      <view class="a-tags">
        <text v-for="d in aid.disorderTypes" :key="d" class="a-tag">{{ disorderLabel(d) }}</text>
      </view>
      <text class="a-guide">{{ aid.usageGuide }}</text>
    </view>
    <view v-if="!list.length && !loading" class="empty"><text>暂无匹配教具</text></view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { recommendAids } from '../../api/aids'
import { disorderLabel, DISORDER_OPTIONS } from '../../utils/format'
import { useUserStore } from '../../store/user'

const userStore = useUserStore()
// 第一项为「全部」
const options = [{ value: '', label: '全部' }, ...DISORDER_OPTIONS]
const typeRange = options.map((o) => o.label)
const typeIndex = ref(0)
const list = ref([])
const loading = ref(false)

onShow(() => {
  if (!userStore.token) { uni.reLaunch({ url: '/pages/login/login' }); return }
  load()
})

function onPick(e) {
  typeIndex.value = Number(e.detail.value)
  load()
}

async function load() {
  loading.value = true
  try { list.value = await recommendAids(options[typeIndex.value].value) }
  catch (e) { /* toast */ } finally { loading.value = false }
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.filter { display: flex; justify-content: space-between; align-items: center; }
.picker { font-size: 30rpx; color: #3b82f6; }
.asset-entry { font-size: 28rpx; color: #8b5cf6; }
.a-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
.a-name { font-size: 32rpx; font-weight: bold; }
.a-cat { font-size: 24rpx; color: #888; }
.a-tags { display: flex; flex-wrap: wrap; margin-bottom: 12rpx; }
.a-tag { background: #eff6ff; color: #3b82f6; font-size: 22rpx; padding: 4rpx 14rpx; border-radius: 16rpx; margin: 0 12rpx 8rpx 0; }
.a-guide { font-size: 28rpx; color: #555; line-height: 1.6; }
.empty { text-align: center; color: #bbb; margin-top: 120rpx; }
</style>
