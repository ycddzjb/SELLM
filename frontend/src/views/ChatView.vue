<template>
  <div class="chat">
    <header class="bar">
      <span class="logo" @click="$router.push('/landing')">← 特殊教育大模型 · 问答机器人</span>
      <div>
        <span v-if="auth.isLoggedIn" style="margin-right:12px;color:#606266">{{ auth.username }}</span>
        <el-button v-if="!auth.isLoggedIn" size="small" type="primary" @click="$router.push('/login')">登录</el-button>
        <el-button v-else size="small" @click="$router.push('/dashboard')">进入工作台</el-button>
      </div>
    </header>

    <div ref="scrollRef" class="msgs">
      <div v-if="!messages.length" class="empty">
        <div class="big">🤖</div>
        <p>你好,我是特殊教育问答助手。可提问政策/专业知识,或上传文档、图片让我分析。</p>
        <p class="hint">试试:「孤独症融合教育有哪些政策?」</p>
      </div>
      <div v-for="(m,i) in messages" :key="i" :class="['row', m.role]">
        <div class="bubble">
          <div v-if="m.file" class="file-chip">📎 {{ m.file }}</div>
          <div class="text">{{ m.content }}</div>
          <div v-if="m.routeTo" class="route">→ 建议前往:<a @click="goRoute(m.deepLink)">{{ m.routeTo }}</a></div>
          <div v-if="m.sources && m.sources.length" class="src">
            来源:{{ m.sources.map(s => s.title || s.source).join('、') }}
          </div>
        </div>
      </div>
      <div v-if="loading" class="row assistant"><div class="bubble"><span class="typing">思考中…</span></div></div>
    </div>

    <div class="composer">
      <div v-if="pendingFile" class="pending">📎 {{ pendingFile.name }} <el-button link @click="pendingFile=null">移除</el-button></div>
      <div class="input-row">
        <el-upload :show-file-list="false" :before-upload="onPickFile" accept="image/*,.pdf,.txt,.md,.json">
          <el-button :icon="Paperclip" circle title="上传文档/图片" />
        </el-upload>
        <el-input v-model="input" type="textarea" :rows="1" autosize resize="none"
                  placeholder="输入问题,Enter 发送(Shift+Enter 换行)" @keydown.enter.exact.prevent="onSend" />
        <el-button type="primary" :loading="loading" @click="onSend">发送</el-button>
      </div>
      <p class="disclaimer">AI 生成内容仅供参考,需专业人员把关。匿名提问不保存历史。</p>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Paperclip } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { ask, analyzeDoc } from '../api/qa'

const auth = useAuthStore()
const router = useRouter()
const messages = ref([])
const input = ref('')
const loading = ref(false)
const pendingFile = ref(null)
const conversationId = ref(null)
const scrollRef = ref(null)

function onPickFile(file) { pendingFile.value = file; return false }  // 阻止自动上传

async function scrollBottom() {
  await nextTick()
  if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
}

function goRoute(link) {
  if (!auth.isLoggedIn) { router.push('/login'); return }
  if (link) router.push(link)
}

async function onSend() {
  const q = input.value.trim()
  const file = pendingFile.value
  if (!q && !file) return
  loading.value = true

  if (file) {
    // 文档/图片分析
    messages.value.push({ role: 'user', content: q || '(请分析此文件)', file: file.name })
    input.value = ''; pendingFile.value = null
    await scrollBottom()
    try {
      const result = await analyzeDoc(file, q)
      messages.value.push({ role: 'assistant', content: result })
    } catch (e) {
      messages.value.push({ role: 'assistant', content: '分析失败,请重试。' })
    } finally { loading.value = false; scrollBottom() }
    return
  }

  // 纯文本问答
  messages.value.push({ role: 'user', content: q })
  input.value = ''
  await scrollBottom()
  try {
    const r = await ask({ question: q, conversationId: conversationId.value })
    if (r.conversationId) conversationId.value = r.conversationId
    messages.value.push({ role: 'assistant', content: r.answer, routeTo: r.routeTo, deepLink: r.deepLink, sources: r.sources })
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '服务暂不可用,请稍后重试。' })
  } finally { loading.value = false; scrollBottom() }
}
</script>

<style scoped>
.chat { display:flex; flex-direction:column; height:100vh; background:#f7f8fa; }
.bar { display:flex; justify-content:space-between; align-items:center; padding:12px 24px; background:#fff; border-bottom:1px solid #ebeef5; }
.logo { font-weight:600; color:#2c3e50; cursor:pointer; }
.msgs { flex:1; overflow-y:auto; padding:24px; max-width:860px; width:100%; margin:0 auto; box-sizing:border-box; }
.empty { text-align:center; color:#909399; margin-top:60px; }
.empty .big { font-size:48px; }
.empty .hint { font-size:13px; color:#c0c4cc; }
.row { display:flex; margin-bottom:16px; }
.row.user { justify-content:flex-end; }
.bubble { max-width:75%; padding:12px 16px; border-radius:12px; background:#fff; box-shadow:0 1px 3px rgba(0,0,0,.06); }
.row.user .bubble { background:#d9ecff; }
.text { white-space:pre-wrap; line-height:1.6; }
.file-chip { font-size:12px; color:#409eff; margin-bottom:6px; }
.route { margin-top:8px; font-size:13px; color:#409eff; }
.route a { cursor:pointer; text-decoration:underline; }
.src { margin-top:8px; font-size:12px; color:#909399; }
.typing { color:#909399; }
.composer { background:#fff; border-top:1px solid #ebeef5; padding:12px 24px; }
.pending { max-width:820px; margin:0 auto 8px; font-size:13px; color:#409eff; }
.input-row { display:flex; gap:10px; align-items:flex-end; max-width:820px; margin:0 auto; }
.disclaimer { text-align:center; font-size:12px; color:#c0c4cc; margin:8px 0 0; }
</style>
