<template>
  <div class="dash">
    <div class="hero">
      <h2>特殊教育垂直大模型平台</h2>
      <p>欢迎,{{ auth.username }} <el-tag size="small">{{ auth.roleLabel }}</el-tag>
        <span v-if="auth.orgLabel" style="color:#909399"> · {{ auth.orgLabel }}</span>
      </p>
      <p class="sub">五大智能体协同:问答 · 教学训练 · 评估干预 · 智能教具 · 教研科研。选择一个开始工作。</p>
    </div>

    <div class="grid">
      <div v-for="m in visibleModules" :key="m.path" class="card" @click="go(m.path)">
        <div class="icon">{{ m.icon }}</div>
        <div class="title">{{ m.title }}</div>
        <div class="desc">{{ m.desc }}</div>
        <el-button text type="primary" class="enter">进入工作台 →</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const modules = [
  { path: '/qa', icon: '🤖', title: '问答机器人', desc: '政策法规、专业知识问答,RAG 检索增强,业务意图智能引导', roles: ['SUPER_ADMIN', 'MANAGER', 'TEACHER', 'PARENT'] },
  { path: '/teaching', icon: '📚', title: '教学训练', desc: '据定稿 IEP 生成教案与课件草案,人工定稿', roles: ['SUPER_ADMIN', 'TEACHER'] },
  { path: '/assessment', icon: '📋', title: '评估干预', desc: '量表评估打分 → 生成报告 → 个别化教育计划(IEP)', roles: ['SUPER_ADMIN', 'TEACHER'] },
  { path: '/aids', icon: '🧩', title: '智能教具', desc: '按障碍类型推荐教具,文生教学素材(插图/绘本/音视频)', roles: ['SUPER_ADMIN', 'TEACHER'] },
  { path: '/research', icon: '🔬', title: '教研科研', desc: '课题申报书生成,量表信效度(Cronbach α)计算', roles: ['SUPER_ADMIN', 'TEACHER'] }
]

const visibleModules = computed(() => modules.filter(m => m.roles.includes(auth.role)))

function go(path) { router.push(path) }
</script>

<style scoped>
.dash { padding: 8px 4px; }
.hero h2 { margin: 0 0 6px; }
.hero .sub { color: #909399; margin-top: 4px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 16px; margin-top: 24px; }
.card {
  background: #fff; border: 1px solid #ebeef5; border-radius: 10px; padding: 20px;
  cursor: pointer; transition: all .2s; box-shadow: 0 1px 4px rgba(0,0,0,.04);
}
.card:hover { transform: translateY(-3px); box-shadow: 0 6px 18px rgba(0,0,0,.1); border-color: #c6e2ff; }
.icon { font-size: 36px; }
.title { font-size: 18px; font-weight: 600; margin: 10px 0 6px; }
.desc { color: #909399; font-size: 13px; line-height: 1.6; min-height: 42px; }
.enter { padding: 0; margin-top: 10px; }
</style>
