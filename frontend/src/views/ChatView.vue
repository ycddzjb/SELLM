<template>
  <div class="chat">
    <!-- 左侧:多会话列表 -->
    <aside class="side">
      <div class="side-top">
        <span class="logo" @click="$router.push('/landing')">← 问答机器人</span>
      </div>
      <el-button class="new-btn" type="primary" plain @click="newConversation">+ 新建对话</el-button>
      <div class="conv-list">
        <div v-for="c in conversations" :key="c.id"
             :class="['conv', c.id === activeId ? 'active' : '']" @click="switchConv(c.id)">
          <span class="conv-title">{{ c.title }}</span>
          <el-icon class="del" @click.stop="closeConv(c.id)"><Close /></el-icon>
        </div>
      </div>
      <div class="side-foot">
        <el-button v-if="!auth.isLoggedIn" size="small" type="primary" @click="$router.push('/login')">登录</el-button>
        <template v-else>
          <span class="uname">{{ auth.username }}</span>
          <el-button size="small" @click="$router.push('/dashboard')">工作台</el-button>
        </template>
      </div>
    </aside>

    <!-- 右侧:对话区 -->
    <main class="main">
      <div ref="scrollRef" class="msgs">
        <!-- 空态:展示模板与示例 -->
        <div v-if="!active.messages.length" class="welcome">
          <div class="big">🤖</div>
          <p class="hi">你好,我是特殊教育问答助手。选个示例直接问,或用模板快速提问。</p>

          <div class="block">
            <div class="block-title">💡 提问示例(点击直接问)</div>
            <div class="chips">
              <span v-for="(ex,i) in examples" :key="i" class="chip" @click="askExample(ex)">{{ ex }}</span>
            </div>
          </div>

          <div class="block">
            <div class="block-title">📝 提问模板(填空后提问)</div>
            <div class="tpls">
              <div v-for="(t,i) in templates" :key="i" class="tpl" @click="useTemplate(t)">
                <span class="tpl-name">{{ t.title }}</span>
                <span class="tpl-text">{{ t.template }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 消息 -->
        <div v-for="(m,i) in active.messages" :key="i" :class="['row', m.role]">
          <div class="bubble">
            <div v-if="m.file" class="file-chip">📎 {{ m.file }}</div>
            <div class="text">{{ m.content }}</div>
            <div v-if="m.routeTo" class="route">→ 建议前往:<a @click="goRoute(m.deepLink)">{{ m.routeTo }}</a></div>
            <div v-if="m.sources && m.sources.length" class="src">来源:{{ m.sources.map(s => s.title || s.source).join('、') }}</div>
          </div>
        </div>
        <div v-if="loading" class="row assistant"><div class="bubble"><span class="typing">思考中…</span></div></div>
      </div>

      <!-- 输入区 -->
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
    </main>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Paperclip, Close } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { ask, analyzeDoc } from '../api/qa'
import { QUESTION_TEMPLATES, QUESTION_EXAMPLES } from '../api/qaPresets'

const auth = useAuthStore()
const router = useRouter()
const templates = QUESTION_TEMPLATES
const examples = QUESTION_EXAMPLES

// 多会话:每个会话 {id, title, messages[], conversationId(后端,登录时)}
let seq = 1
function blankConv() { return { id: seq++, title: '新对话', messages: [], conversationId: null } }
const conversations = ref([blankConv()])
const activeId = ref(conversations.value[0].id)
const active = computed(() => conversations.value.find(c => c.id === activeId.value) || conversations.value[0])

const input = ref('')
const loading = ref(false)
const pendingFile = ref(null)
const scrollRef = ref(null)

function newConversation() {
  const c = blankConv()
  conversations.value.push(c)
  activeId.value = c.id
}
function switchConv(id) { activeId.value = id }
function closeConv(id) {
  const i = conversations.value.findIndex(c => c.id === id)
  if (i < 0) return
  conversations.value.splice(i, 1)
  if (!conversations.value.length) conversations.value.push(blankConv())
  if (activeId.value === id) activeId.value = conversations.value[0].id
}

function onPickFile(file) { pendingFile.value = file; return false }
async function scrollBottom() { await nextTick(); if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight }
function goRoute(link) { if (!auth.isLoggedIn) { router.push('/login'); return } if (link) router.push(link) }

function useTemplate(t) { input.value = t.template }      // 填到输入框,用户补全占位再发
function askExample(ex) { input.value = ex; onSend() }    // 示例直接发起问答

async function onSend() {
  const q = input.value.trim()
  const file = pendingFile.value
  if (!q && !file) return
  const conv = active.value
  // 首条消息用问题前 20 字做会话标题
  if (!conv.messages.length) conv.title = (q || (file && file.name) || '新对话').slice(0, 20)
  loading.value = true

  if (file) {
    conv.messages.push({ role: 'user', content: q || '(请分析此文件)', file: file.name })
    input.value = ''; pendingFile.value = null
    await scrollBottom()
    try {
      const result = await analyzeDoc(file, q)
      conv.messages.push({ role: 'assistant', content: result })
    } catch (e) { conv.messages.push({ role: 'assistant', content: '分析失败,请重试。' }) }
    finally { loading.value = false; scrollBottom() }
    return
  }

  conv.messages.push({ role: 'user', content: q })
  input.value = ''
  await scrollBottom()
  try {
    const r = await ask({ question: q, conversationId: conv.conversationId })
    if (r.conversationId) conv.conversationId = r.conversationId
    conv.messages.push({ role: 'assistant', content: r.answer, routeTo: r.routeTo, deepLink: r.deepLink, sources: r.sources })
  } catch (e) { conv.messages.push({ role: 'assistant', content: '服务暂不可用,请稍后重试。' }) }
  finally { loading.value = false; scrollBottom() }
}
</script>

<style scoped>
.chat { display:flex; height:100vh; background:#f7f8fa; }
.side { width:240px; background:#fff; border-right:1px solid #ebeef5; display:flex; flex-direction:column; padding:12px; box-sizing:border-box; }
.side-top { padding:4px 4px 12px; }
.logo { font-weight:600; color:#2c3e50; cursor:pointer; font-size:15px; }
.new-btn { width:100%; margin-bottom:12px; }
.conv-list { flex:1; overflow-y:auto; }
.conv { display:flex; justify-content:space-between; align-items:center; padding:10px; border-radius:8px; cursor:pointer; font-size:14px; color:#444; }
.conv:hover { background:#f5f7fa; }
.conv.active { background:#ecf5ff; color:#409eff; }
.conv-title { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.del { opacity:0; }
.conv:hover .del { opacity:.6; }
.side-foot { border-top:1px solid #ebeef5; padding-top:10px; display:flex; align-items:center; justify-content:space-between; }
.uname { font-size:13px; color:#606266; }
.main { flex:1; display:flex; flex-direction:column; }
.msgs { flex:1; overflow-y:auto; padding:24px; }
.welcome { max-width:760px; margin:24px auto; }
.welcome .big { font-size:48px; text-align:center; }
.welcome .hi { text-align:center; color:#606266; margin-bottom:24px; }
.block { margin-bottom:24px; }
.block-title { font-weight:600; color:#303133; margin-bottom:10px; }
.chips { display:flex; flex-wrap:wrap; gap:8px; }
.chip { background:#fff; border:1px solid #dcdfe6; border-radius:16px; padding:6px 14px; font-size:13px; cursor:pointer; transition:all .2s; }
.chip:hover { border-color:#409eff; color:#409eff; }
.tpls { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
.tpl { background:#fff; border:1px solid #ebeef5; border-radius:8px; padding:12px; cursor:pointer; transition:all .2s; }
.tpl:hover { border-color:#409eff; box-shadow:0 2px 8px rgba(64,158,255,.12); }
.tpl-name { display:block; font-weight:600; font-size:13px; color:#409eff; margin-bottom:4px; }
.tpl-text { font-size:12px; color:#909399; line-height:1.5; }
.row { display:flex; margin-bottom:16px; max-width:860px; margin-left:auto; margin-right:auto; }
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
