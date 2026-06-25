import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // 完整平台:默认经网关 8888 路由到 5 大 Agent(评估走 backend,teaching/research/aids/qa 走对应 Agent)。
        // 仅起 backend 单体调试时改回 http://localhost:8080。
        target: process.env.VITE_API_TARGET || 'http://localhost:8888',
        changeOrigin: true
      }
    }
  }
})
