<template>
  <el-container style="height: 100vh">
    <el-aside width="220px">
      <div class="brand">特殊教育垂直大模型</div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/dashboard"><span>🏠 首页</span></el-menu-item>
        <!-- ── 五大 Agent 模块(统一门户主导航)── -->
        <el-menu-item-group title="智能体">
          <el-menu-item index="/qa">
            <span>🤖 问答机器人</span>
          </el-menu-item>
          <el-menu-item v-if="auth.isTeacher || auth.isSuperAdmin" index="/teaching">
            <span>📚 教学训练</span>
          </el-menu-item>
          <el-menu-item v-if="auth.isTeacher || auth.isSuperAdmin" index="/assessment">
            <span>📋 评估干预</span>
          </el-menu-item>
          <el-menu-item v-if="auth.isTeacher || auth.isSuperAdmin" index="/aids">
            <span>🧩 智能教具</span>
          </el-menu-item>
          <el-menu-item v-if="auth.isTeacher || auth.isSuperAdmin" index="/research">
            <span>🔬 教研科研</span>
          </el-menu-item>
        </el-menu-item-group>

        <!-- ── 评估干预 Agent 的关联功能 ── -->
        <el-menu-item-group v-if="auth.isTeacher || auth.isSuperAdmin || auth.isManager" title="评估干预 · 工作台">
          <el-menu-item v-if="auth.isSuperAdmin || auth.isManager || auth.isTeacher" index="/children">儿童档案</el-menu-item>
          <el-menu-item v-if="auth.isTeacher" index="/diagnosis">多模态诊断</el-menu-item>
          <el-menu-item v-if="auth.isTeacher" index="/report">评估报告</el-menu-item>
          <el-menu-item v-if="auth.isTeacher" index="/iep">个别化教育计划</el-menu-item>
          <el-menu-item v-if="auth.isTeacher" index="/training">训练对比评估</el-menu-item>
        </el-menu-item-group>

        <!-- ── 平台管理 ── -->
        <el-menu-item-group title="平台管理">
          <el-menu-item v-if="auth.isSuperAdmin" index="/scale-library">量表库管理</el-menu-item>
          <el-menu-item v-if="auth.isManager" index="/classes">班级管理</el-menu-item>
          <el-menu-item v-if="auth.isSuperAdmin || auth.isManager || auth.isTeacher" index="/users">用户管理</el-menu-item>
        </el-menu-item-group>

        <!-- ── 家长 ── -->
        <el-menu-item-group v-if="auth.isParent" title="家长">
          <el-menu-item index="/children">我的孩子</el-menu-item>
          <el-menu-item index="/family-iep">家庭 IEP</el-menu-item>
        </el-menu-item-group>
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
</style>
