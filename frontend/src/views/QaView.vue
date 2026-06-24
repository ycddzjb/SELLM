<template>
  <div>
    <h3>问答机器人</h3>
    <div style="display:flex;gap:16px;max-width:1000px">
      <!-- 会话列表 -->
      <el-card style="width:220px;flex-shrink:0">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
          <span>会话</span><el-button link type="primary" @click="newChat">新建</el-button>
        </div>
        <div v-for="c in conversations" :key="c.id"
             :class="['conv-item', c.id===conversationId ? 'active' : '']" @click="openConv(c.id)">
          {{ c.title || ('会话 #' + c.id) }}
        </div>
      </el-card>

      <!-- 对话区 -->
      <el-card style="flex:1">
        <div class="msgs">
          <div v-for="(m,i) in messages" :key="i" :class="['msg', m.role==='USER'?'user':'assistant']">
            <div class="bubble">
              {{ m.content }}
              <div v-if="m.routeTo" style="margin-top:6px;font-size:12px;color:#3b82f6">→ 建议前往:{{ m.routeTo }}</div>
              <div v-if="m.sources && m.sources.length" style="margin-top:6px;font-size:12px;color:#888">
                来源:{{ m.sources.map(s => s.title || s.source).join('、') }}
              </div>
            </div>
          </div>
          <el-empty v-if="!messages.length" description="提问试试,如「孤独症融合教育政策有哪些」" />
        </div>
        <div style="display:flex;gap:8px;margin-top:12px">
          <el-input v-model="question" placeholder="输入问题" @keyup.enter="onAsk" />
          <el-button type="primary" :loading="asking" @click="onAsk">发送</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ask, listConversations, getConversation } from '../api/qa'

const conversations = ref([])
const conversationId = ref(null)
const messages = ref([])
const question = ref('')
const asking = ref(false)

onMounted(loadConvs)

async function loadConvs() { try { conversations.value = await listConversations() } catch (e) {} }

function newChat() { conversationId.value = null; messages.value = [] }

async function openConv(id) {
  try {
    const c = await getConversation(id)
    conversationId.value = id
    messages.value = c.messages || []
  } catch (e) {}
}

async function onAsk() {
  if (!question.value.trim()) return
  const q = question.value
  messages.value.push({ role: 'USER', content: q })
  question.value = ''
  asking.value = true
  try {
    const r = await ask({ question: q, conversationId: conversationId.value })
    conversationId.value = r.conversationId
    messages.value.push({ role: 'ASSISTANT', content: r.answer, routeTo: r.routeTo, sources: r.sources })
    loadConvs()
  } catch (e) {} finally { asking.value = false }
}
</script>

<style scoped>
.conv-item { padding:8px; border-radius:4px; cursor:pointer; font-size:14px; }
.conv-item:hover { background:#f5f7fa; }
.conv-item.active { background:#ecf5ff; color:#409eff; }
.msgs { min-height:320px; max-height:480px; overflow-y:auto; }
.msg { display:flex; margin-bottom:10px; }
.msg.user { justify-content:flex-end; }
.bubble { max-width:75%; padding:8px 12px; border-radius:8px; background:#f4f4f5; white-space:pre-wrap; }
.msg.user .bubble { background:#ecf5ff; }
</style>
