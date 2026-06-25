<template>
  <div class="landing">
    <header class="top">
      <span class="logo">特殊教育垂直大模型平台</span>
      <el-button type="primary" @click="$router.push('/login')">登录 / 注册</el-button>
    </header>

    <section class="hero">
      <h1>五大智能体,协同赋能特殊教育</h1>
      <p>问答 · 教学训练 · 评估干预 · 智能教具 · 教研科研。无需登录,即可体验智能问答。</p>
      <el-button type="primary" size="large" @click="$router.push('/chat')">免登录体验问答 →</el-button>
    </section>

    <section class="grid">
      <div v-for="m in modules" :key="m.title" class="card" @click="enter(m)">
        <div class="icon">{{ m.icon }}</div>
        <div class="title">{{ m.title }}</div>
        <div class="desc">{{ m.desc }}</div>
        <el-tag v-if="m.public" type="success" size="small">免登录体验</el-tag>
        <el-tag v-else size="small" type="info">登录使用</el-tag>
      </div>
    </section>

    <footer class="foot">© 特殊教育垂直大模型平台 · 仅供专业人员使用,AI 内容需人工把关</footer>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
const modules = [
  { icon: '🤖', title: '问答机器人', desc: '政策法规与专业知识问答,支持文档/图片分析', public: true, path: '/chat' },
  { icon: '📚', title: '教学训练', desc: '据 IEP 生成教案与课件草案', public: false },
  { icon: '📋', title: '评估干预', desc: '量表评估 → 报告 → 个别化教育计划', public: false },
  { icon: '🧩', title: '智能教具', desc: '障碍类型教具推荐与文生素材', public: false },
  { icon: '🔬', title: '教研科研', desc: '课题申报书与量表信效度计算', public: false }
]
function enter(m) { router.push(m.public ? m.path : '/login') }
</script>

<style scoped>
.landing { min-height: 100vh; background: linear-gradient(180deg,#f0f6ff,#fff); }
.top { display:flex; justify-content:space-between; align-items:center; padding:16px 40px; background:#fff; box-shadow:0 1px 6px rgba(0,0,0,.05); }
.logo { font-size:18px; font-weight:600; color:#2c3e50; }
.hero { text-align:center; padding:60px 20px 40px; }
.hero h1 { font-size:32px; margin:0 0 12px; color:#1f2d3d; }
.hero p { color:#5e6d82; font-size:16px; margin-bottom:24px; }
.grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(220px,1fr)); gap:18px; max-width:1100px; margin:0 auto; padding:0 40px 50px; }
.card { background:#fff; border:1px solid #ebeef5; border-radius:12px; padding:24px; cursor:pointer; transition:all .2s; }
.card:hover { transform:translateY(-4px); box-shadow:0 8px 24px rgba(0,0,0,.1); }
.icon { font-size:40px; }
.title { font-size:18px; font-weight:600; margin:12px 0 6px; }
.desc { color:#909399; font-size:13px; line-height:1.6; min-height:40px; margin-bottom:10px; }
.foot { text-align:center; color:#a0a0a0; font-size:12px; padding:24px; }
</style>
