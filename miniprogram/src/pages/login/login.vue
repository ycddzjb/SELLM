<template>
  <view class="login">
    <view class="brand">
      <text class="logo">SELLM</text>
      <text class="slogan">特殊教育 · 家长助手</text>
    </view>
    <view class="form">
      <input class="field" v-model="username" placeholder="用户名" />
      <input class="field" v-model="password" type="password" placeholder="密码" />
      <button class="btn" :loading="loading" @click="onLogin">登录</button>
      <view class="divider"><text>或</text></view>
      <!-- #ifdef MP-WEIXIN -->
      <button class="btn wx" :loading="wxLoading" @click="onWechatLogin">微信一键登录</button>
      <!-- #endif -->
      <text class="hint">建议使用家长账号登录</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { login, wechatLogin } from '../../api/auth'
import { useUserStore } from '../../store/user'

const username = ref('')
const password = ref('')
const loading = ref(false)
const wxLoading = ref(false)
const userStore = useUserStore()

async function onLogin() {
  if (!username.value || !password.value) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const data = await login(username.value, password.value)
    userStore.setAuth({ token: data.token, role: data.role, username: data.username })
    uni.reLaunch({ url: '/pages/children/children' })
  } catch (e) {
    // toast 已在 http 拦截器处理
  } finally {
    loading.value = false
  }
}

/** 微信一键登录:uni.login 取 code → 换 token。 */
function onWechatLogin() {
  wxLoading.value = true
  uni.login({
    provider: 'weixin',
    success: async (res) => {
      if (!res.code) {
        uni.showToast({ title: '微信授权失败', icon: 'none' })
        wxLoading.value = false
        return
      }
      try {
        const data = await wechatLogin(res.code)
        userStore.setAuth({ token: data.token, role: data.role, username: data.username })
        uni.reLaunch({ url: '/pages/children/children' })
      } catch (e) {
        // toast 已在拦截器处理(含"账号待审核")
      } finally {
        wxLoading.value = false
      }
    },
    fail: () => {
      uni.showToast({ title: '微信登录失败', icon: 'none' })
      wxLoading.value = false
    },
  })
}
</script>

<style scoped>
.login { padding: 120rpx 60rpx 0; }
.brand { display: flex; flex-direction: column; align-items: center; margin-bottom: 100rpx; }
.logo { font-size: 72rpx; font-weight: bold; color: #3b82f6; letter-spacing: 4rpx; }
.slogan { font-size: 28rpx; color: #999; margin-top: 16rpx; }
.form { display: flex; flex-direction: column; }
.field {
  background: #fff; border-radius: 12rpx; padding: 28rpx 24rpx;
  margin-bottom: 28rpx; font-size: 30rpx;
}
.btn {
  background: #3b82f6; color: #fff; border-radius: 12rpx;
  margin-top: 20rpx; font-size: 32rpx;
}
.btn.wx { background: #07c160; }
.divider { display: flex; align-items: center; justify-content: center; margin: 30rpx 0 10rpx; color: #bbb; font-size: 24rpx; }
.hint { text-align: center; color: #aaa; font-size: 24rpx; margin-top: 30rpx; }
</style>
