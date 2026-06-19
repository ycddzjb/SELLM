<template>
  <el-container style="height: 100vh">
    <el-aside width="200px">
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/children">儿童档案</el-menu-item>
        <el-menu-item index="/assessment">评估</el-menu-item>
        <el-menu-item index="/report">报告</el-menu-item>
        <el-menu-item index="/iep">IEP</el-menu-item>
        <el-menu-item v-if="auth.isManager || auth.isSuperAdmin" index="/users">用户管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="display:flex;align-items:center;justify-content:space-between">
        <span>特殊教育评估助手 · 管理端</span>
        <div style="display:flex;align-items:center;gap:12px">
          <span style="color:#606266;font-size:14px">
            {{ auth.username }}
            <el-tag size="small" type="primary" style="margin:0 4px">{{ auth.roleLabel }}</el-tag>
            <span v-if="auth.orgLabel" style="color:#909399">· {{ auth.orgLabel }}</span>
          </span>
          <el-button size="small" @click="onLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'
const auth = useAuthStore()
const router = useRouter()
function onLogout() {
  auth.logout()
  router.push('/login')
}
</script>
