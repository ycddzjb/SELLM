<template>
  <el-container style="height: 100vh">
    <el-aside width="220px">
      <div class="brand">特殊教育垂直大模型</div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/dashboard"><span>🏠 首页</span></el-menu-item>

        <!-- ══ 平台一:智能体平台 ══ -->
        <el-menu-item-group title="智能体平台">
          <el-menu-item index="/qa"><span>🤖 问答机器人</span></el-menu-item>
          <el-menu-item v-if="auth.isTeacher || auth.isSuperAdmin" index="/teaching">
            <span>📚 智能教学</span>
          </el-menu-item>
          <!-- 智能评估 + 全流程子项(紧随智能教学) -->
          <el-menu-item v-if="auth.isTeacher || auth.isSuperAdmin" index="/diagnosis">
            <span>🩺 智能评估</span>
          </el-menu-item>
          <template v-if="auth.isTeacher">
            <el-menu-item index="/assessment"><span class="sub">· 量表评估</span></el-menu-item>
            <el-menu-item index="/report"><span class="sub">· 评估报告</span></el-menu-item>
            <el-menu-item index="/iep"><span class="sub">· 个别化教育计划</span></el-menu-item>
            <el-menu-item index="/training"><span class="sub">· 训练对比评估</span></el-menu-item>
          </template>
          <el-menu-item v-if="auth.isTeacher || auth.isSuperAdmin" index="/aids">
            <span>🧩 智能教具</span>
          </el-menu-item>
          <el-menu-item v-if="auth.isTeacher || auth.isSuperAdmin" index="/research">
            <span>🔬 智能科研</span>
          </el-menu-item>
          <el-menu-item v-if="auth.isParent" index="/family-iep">
            <span>🏠 家庭 IEP</span>
          </el-menu-item>
        </el-menu-item-group>

        <!-- ══ 平台二:数据管理平台 ══ -->
        <el-menu-item-group title="数据管理平台">
          <el-menu-item index="/children">
            <span>👦 儿童档案</span>
          </el-menu-item>
          <el-menu-item v-if="auth.isSuperAdmin" index="/scale-library">
            <span>📚 量表库管理</span>
          </el-menu-item>
        </el-menu-item-group>

        <!-- ══ 平台三:用户管理平台 ══ -->
        <el-menu-item-group title="用户管理平台">
          <el-menu-item index="/users">
            <span>👤 用户中心</span>
          </el-menu-item>
          <el-menu-item v-if="auth.isManager" index="/classes">
            <span>🏫 班级管理</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="display:flex;align-items:center;justify-content:flex-end">
        <div style="display:flex;align-items:center;gap:12px">
          <span style="color:#606266;font-size:14px">
            {{ auth.username }}
            <el-tag size="small" type="primary" style="margin:0 4px">{{ auth.roleLabel }}</el-tag>
            <span v-if="auth.orgLabel" style="color:#909399">· {{ auth.orgLabel }}</span>
          </span>
          <el-button size="small" @click="$router.push('/dashboard')">首页</el-button>
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

<style scoped>
.brand {
  height: 56px;
  line-height: 56px;
  text-align: center;
  font-weight: 600;
  font-size: 16px;
  color: #fff;
  background: #2c3e50;
  letter-spacing: 1px;
}
.el-aside { background: #fff; border-right: 1px solid #ebeef5; }
.sub { color: #909399; font-size: 13px; padding-left: 8px; }
</style>
