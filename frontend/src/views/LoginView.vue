<template>
  <div style="display:flex;align-items:center;justify-content:center;height:100vh;background:#f0f2f5">
    <el-card style="width:360px">
      <h2 style="text-align:center;margin-bottom:20px">特教评估助手 · 登录</h2>
      <el-form @submit.prevent="onSubmit">
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-button type="primary" style="width:100%" :loading="loading" @click="onSubmit">登录</el-button>
      </el-form>
      <p style="color:#999;font-size:12px;margin-top:12px">dev 种子账号:admin / admin123</p>
      <p style="text-align:center;margin-top:8px">
        <router-link to="/register">家长注册</router-link>
      </p>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const username = ref('')
const password = ref('')
const loading = ref(false)
const router = useRouter()
const auth = useAuthStore()

async function onSubmit() {
  if (!username.value || !password.value) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const data = await login(username.value, password.value)
    auth.setAuth({ token: data.token, role: data.role, username: data.username, orgName: data.orgName })
    ElMessage.success('登录成功')
    router.push('/children')
  } catch (e) {
    // http 拦截器已弹错
  } finally {
    loading.value = false
  }
}
</script>
