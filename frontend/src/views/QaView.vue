<template>
  <div class="qa">
    <!-- 左侧:会话列表(登录态走后端持久化) -->
    <aside class="side">
      <el-button class="new-btn" type="primary" plain @click="newConversation">+ 新建对话</el-button>
      <div class="conv-list">
        <div v-for="c in conversations" :key="c.id"
             :class="['conv', c.id === activeId ? 'active' : '']" @click="switchConv(c.id)">
          <span class="conv-title">{{ c.title || ('会话 #' + c.id) }}</span>
          <el-icon class="del" @click.stop="closeConv(c.id)"><Close /></el-icon>
        </div>
        <el-empty v-if="!conversations.length" description="暂无历史会话" :image-size="60" />
      </div>
    </aside>

    <!-- 右侧:对话区 -->
    <main class="main">
      <div ref="scrollRef" class="msgs">
        <!-- 空态:展示模板与示例 -->
        <div v-if="!messages.length" class="welcome">
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
        <div v-for="(m,i) in messages" :key="i" :class="['row', msgRole(m)]">
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
        <p class="disclaimer">AI 生成内容仅供参考,需专业人员把关。</p>
      </div>
    </main>
  </div>
</template>
<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Paperclip, Close } from '@element-plus/icons-vue'
import { ask, analyzeDoc, listConversations, getConversation } from '../api/qa'
import { QUESTION_TEMPLATES, QUESTION_EXAMPLES } from '../api/qaPresets'

const router = useRouter()
const templates = QUESTION_TEMPLATES
const examples = QUESTION_EXAMPLES

// 会话:登录态走后端持久化。activeId=后端会话 id;null 表示新对话(未落库)。
const conversations = ref([])
const activeId = ref(null)
const messages = ref([])
const input = ref('')
const loading = ref(false)
const pendingFile = ref(null)
const scrollRef = ref(null)

onMounted(loadConvs)

async function loadConvs() {
  try { conversations.value = await listConversations() } catch (e) {}
}

function newConversation() { activeId.value = null; messages.value = [] }

async function switchConv(id) {
  if (id === activeId.value) return
  try {
    const c = await getConversation(id)
    activeId.value = id
    messages.value = (c.messages || []).map(m => ({
      role: (m.role || '').toLowerCase(), content: m.content,
      routeTo: m.routeTo, deepLink: m.deepLink, sources: m.sources
    }))
    scrollBottom()
  } catch (e) {}
}

function closeConv(id) {
  // 仅从列表移除本地引用(后端持久化历史不删);若关的是当前会话则回到新对话
  conversations.value = conversations.value.filter(c => c.id !== id)
  if (activeId.value === id) newConversation()
}

function msgRole(m) { return (m.role || '').toLowerCase() === 'user' ? 'user' : 'assistant' }

function onPickFile(file) { pendingFile.value = file; return false }
async function scrollBottom() { await nextTick(); if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight }
function goRoute(link) { if (link) router.push(link) }

function useTemplate(t) { input.value = t.template }      // 填到输入框,补全占位再发
function askExample(ex) { input.value = ex; onSend() }    // 示例直接发起

async function onSend() {
  const q = input.value.trim()
  const file = pendingFile.value
  if (!q && !file) return
  loading.value = true

  if (file) {
    messages.value.push({ role: 'user', content: q || '(请分析此文件)', file: file.name })
    input.value = ''; pendingFile.value = null
    await scrollBottom()
    try {
      const result = await analyzeDoc(file, q)
      messages.value.push({ role: 'assistant', content: result })
    } catch (e) { messages.value.push({ role: 'assistant', content: '分析失败,请重试。' }) }
    finally { loading.value = false; scrollBottom() }
    return
  }

  messages.value.push({ role: 'user', content: q })
  input.value = ''
  await scrollBottom()
  try {
    const r = await ask({ question: q, conversationId: activeId.value })
    if (r.conversationId) {
      const isNew = activeId.value !== r.conversationId
      activeId.value = r.conversationId
      if (isNew) loadConvs()   // 新会话落库后刷新左侧列表
    }
    messages.value.push({ role: 'assistant', content: r.answer, routeTo: r.routeTo, deepLink: r.deepLink, sources: r.sources })
  } catch (e) { messages.value.push({ role: 'assistant', content: '服务暂不可用,请稍后重试。' }) }
  finally { loading.value = false; scrollBottom() }
}
</script>

<style scoped>
.qa { display:flex; gap:16px; height:calc(100vh - 120px); }
.side { width:240px; flex-shrink:0; background:#fff; border:1px solid #ebeef5; border-radius:8px; display:flex; flex-direction:column; padding:12px; box-sizing:border-box; }
.new-btn { width:100%; margin-bottom:12px; }
.conv-list { flex:1; overflow-y:auto; }
.conv { display:flex; justify-content:space-between; align-items:center; padding:10px; border-radius:8px; cursor:pointer; font-size:14px; color:#444; }
.conv:hover { background:#f5f7fa; }
.conv.active { background:#ecf5ff; color:#409eff; }
.conv-title { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.del { opacity:0; }
.conv:hover .del { opacity:.6; }
.main { flex:1; display:flex; flex-direction:column; background:#fff; border:1px solid #ebeef5; border-radius:8px; overflow:hidden; }
.msgs { flex:1; overflow-y:auto; padding:24px; }
.welcome { max-width:760px; margin:12px auto; }
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
.bubble { max-width:75%; padding:12px 16px; border-radius:12px; background:#f4f4f5; box-shadow:0 1px 3px rgba(0,0,0,.06); }
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
