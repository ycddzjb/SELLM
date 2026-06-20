<template>
  <el-container style="height: 100vh">
    <el-aside width="200px">
      <el-menu :default-active="$route.path" router>
        <!-- 儿童档案:超管/管理员/老师可见 -->
        <el-menu-item v-if="auth.isSuperAdmin || auth.isManager || auth.isTeacher" index="/children">儿童档案</el-menu-item>
        <!-- 量表库管理:超管 -->
        <el-menu-item v-if="auth.isSuperAdmin" index="/scale-library">量表库管理</el-menu-item>
        <!-- 班级管理:机构管理员 -->
        <el-menu-item v-if="auth.isManager" index="/classes">班级管理</el-menu-item>
        <!-- 评估 / IEP:仅老师/康复师 -->
        <el-menu-item v-if="auth.isTeacher" index="/assessment">评估</el-menu-item>
        <el-menu-item v-if="auth.isTeacher" index="/iep">IEP</el-menu-item>
        <!-- 用户管理:超管/管理员/老师 -->
        <el-menu-item v-if="auth.isSuperAdmin || auth.isManager || auth.isTeacher" index="/users">用户管理</el-menu-item>
        <!-- 家长:家庭 IEP + 个人(改密码在用户管理页,这里给家庭IEP入口) -->
        <el-menu-item v-if="auth.isParent" index="/family-iep">家庭 IEP</el-menu-item>
        <el-menu-item v-if="auth.isParent" index="/children">我的孩子</el-menu-item>
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
