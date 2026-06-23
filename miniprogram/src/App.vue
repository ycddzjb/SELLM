<script setup>
import { onLaunch } from '@dcloudio/uni-app'
import { useUserStore } from './store/user'

onLaunch(() => {
  // 恢复持久化登录态;未登录由各页 onShow 拦截跳登录
  const userStore = useUserStore()
  userStore.restore()
})
</script>

<style>
page {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  font-size: 28rpx;
  color: #333;
  background-color: #f5f6f8;
}
</style>
