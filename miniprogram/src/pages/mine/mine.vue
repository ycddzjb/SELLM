<template>
  <view class="page">
    <view class="card profile">
      <view class="avatar">{{ initial }}</view>
      <view class="info">
        <text class="uname">{{ userStore.username || '未登录' }}</text>
        <text class="role">{{ roleLabel }}</text>
      </view>
    </view>
    <button class="logout" @click="onLogout">退出登录</button>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '../../store/user'

const userStore = useUserStore()

const initial = computed(() => (userStore.username || '?').charAt(0).toUpperCase())
const ROLE_LABELS = {
  SUPER_ADMIN: '超级管理员', MANAGER: '机构管理者', TEACHER: '教师', PARENT: '家长',
}
const roleLabel = computed(() => ROLE_LABELS[userStore.role] || userStore.role || '')

onShow(() => {
  if (!userStore.token) { uni.reLaunch({ url: '/pages/login/login' }) }
})

function onLogout() {
  userStore.logout()
  uni.reLaunch({ url: '/pages/login/login' })
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 36rpx; margin-bottom: 30rpx; }
.profile { display: flex; align-items: center; }
.avatar {
  width: 100rpx; height: 100rpx; border-radius: 50rpx; background: #3b82f6; color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 44rpx; margin-right: 28rpx;
}
.info { display: flex; flex-direction: column; }
.uname { font-size: 36rpx; font-weight: bold; }
.role { font-size: 26rpx; color: #999; margin-top: 8rpx; }
.logout { background: #fff; color: #ef4444; border-radius: 12rpx; font-size: 30rpx; border: 1rpx solid #fee2e2; }
</style>
