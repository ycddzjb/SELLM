<template>
  <view class="page">
    <!-- 提醒条 -->
    <view v-if="reminders.length" class="reminders">
      <view v-for="r in reminders" :key="r.childId + r.reminderType"
            class="reminder" :class="{ overdue: r.overdue }">
        <text class="r-name">{{ r.name }}</text>
        <text class="r-text">{{ r.reminderType === 'REASSESS' ? '复评' : 'IEP' }}
          {{ r.overdue ? '已逾期' : r.daysLeft + ' 天后到期' }}</text>
      </view>
    </view>

    <view v-if="children.length" class="list">
      <view v-for="c in children" :key="c.id" class="card" @click="goDetail(c.id)">
        <view class="c-head">
          <text class="c-name">{{ c.name }}</text>
          <text class="c-tag">{{ disorderLabel(c.disorderType) }}</text>
        </view>
        <text v-if="c.monthlyGoal" class="c-goal">月目标:{{ c.monthlyGoal }}</text>
        <text v-else class="c-empty">暂无月目标</text>
      </view>
    </view>
    <view v-else-if="!loading" class="empty">
      <text>暂无可见的孩子</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listChildren, listReminders } from '../../api/child'
import { useUserStore } from '../../store/user'
import { disorderLabel } from '../../utils/format'

const children = ref([])
const reminders = ref([])
const loading = ref(false)
const userStore = useUserStore()

onShow(() => {
  if (!userStore.token) {
    uni.reLaunch({ url: '/pages/login/login' })
    return
  }
  load()
})

async function load() {
  loading.value = true
  try {
    children.value = await listChildren()
    reminders.value = await listReminders()
  } catch (e) { /* toast handled */ } finally {
    loading.value = false
  }
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/child/detail?id=${id}` })
}
</script>

<style scoped>
.page { padding: 24rpx; }
.reminders { margin-bottom: 20rpx; }
.reminder {
  background: #fff7ed; border-left: 8rpx solid #f59e0b; border-radius: 8rpx;
  padding: 18rpx 24rpx; margin-bottom: 12rpx; display: flex; justify-content: space-between;
}
.reminder.overdue { background: #fef2f2; border-left-color: #ef4444; }
.r-name { font-weight: bold; }
.r-text { color: #b45309; font-size: 26rpx; }
.reminder.overdue .r-text { color: #dc2626; }
.list { display: flex; flex-direction: column; }
.card {
  background: #fff; border-radius: 16rpx; padding: 28rpx; margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04);
}
.c-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12rpx; }
.c-name { font-size: 34rpx; font-weight: bold; }
.c-tag { background: #eff6ff; color: #3b82f6; font-size: 24rpx; padding: 6rpx 16rpx; border-radius: 20rpx; }
.c-goal { font-size: 28rpx; color: #555; }
.c-empty { font-size: 26rpx; color: #bbb; }
.empty { text-align: center; color: #bbb; margin-top: 200rpx; }
</style>
