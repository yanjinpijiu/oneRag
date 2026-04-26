<template>
  <div class="page-wrap">
    <div class="chat-layout">
      <el-card class="chat-sidebar" shadow="never">
        <template #header>
          <div class="chat-sidebar-header">
            <span>历史会话</span>
          </div>
        </template>
        <div class="chat-sidebar-actions">
          <el-button type="primary" plain size="small" style="width: 100%" @click="newConversation">+ 新会话</el-button>
        </div>
        <el-skeleton v-if="loadingConversations" :rows="4" animated />
        <div v-else-if="!conversations.length" class="chat-empty">暂无会话</div>
        <div v-else class="chat-conversation-list">
          <div
            v-for="item in conversations"
            :key="item.conversationId"
            :class="['chat-conversation-item', { active: item.conversationId === activeConversationId }]"
            @click="selectConversation(item)">
            <div class="chat-conversation-main">
              <div class="chat-conversation-title">{{ item.title || '未命名会话' }}</div>
              <div class="chat-conversation-time">{{ formatTime(item.lastTime || item.createTime) }}</div>
            </div>
            <el-button link type="danger" @click.stop="removeConversation(item)">删除</el-button>
          </div>
        </div>
      </el-card>

      <div class="chat-content">
        <el-form inline>
          <el-form-item label="深度思考">
            <el-switch v-model="deepThinking" />
          </el-form-item>
        </el-form>

        <el-card class="pipeline-floating" shadow="never">
          <template #header>管线日志</template>
          <pre class="pipeline-log">{{ pipelineLog || '暂无' }}</pre>
        </el-card>

        <el-alert style="margin-top:8px" type="info" :closable="false" :title="statusText" />

        <div ref="messageContainerRef" class="chat-message-list">
          <el-skeleton v-if="loadingMessages" :rows="4" animated />
          <div v-else-if="!messages.length" class="chat-empty">开始提问后会自动创建会话</div>
          <div v-else-if="hasOlderMessages" class="history-toggle">
            <el-button size="small" plain @click="showOlderHistory">显示更早历史对话（10条）</el-button>
          </div>
          <div
            v-for="(item, idx) in visibleMessages"
            :key="`${idx}-${item.role}`"
            :class="['message-row', item.role === 'user' ? 'user' : 'assistant']">
            <div class="message-bubble">
              <div class="message-meta">
                <span>{{ item.role === 'user' ? '我' : '助手' }}</span>
                <span>{{ formatMessageTime(item.createTime) }}</span>
              </div>
              <el-skeleton v-if="item.loading && !item.content" :rows="2" animated />
              <el-collapse v-if="item.reasoning">
                <el-collapse-item title="思考过程">
                  <div style="white-space:pre-wrap">{{ item.reasoning }}</div>
                </el-collapse-item>
              </el-collapse>
              <div style="margin-top:6px;white-space:pre-wrap">{{ item.content }}</div>
              <div v-if="item.model" class="message-model">模型：{{ item.model }}</div>
              <el-collapse v-if="item.references?.length">
                <el-collapse-item title="参考资料">
                  <div v-for="(refItem, refIdx) in item.references" :key="refIdx" style="margin-bottom:10px">
                    <el-card shadow="never">
                      <div><b>[{{ refItem.index }}] {{ refItem.title }}</b></div>
                      <div style="color:#888;font-size:12px">docId={{ refItem.docId }} | score={{ refItem.score }}</div>
                      <div style="white-space:pre-wrap;margin-top:6px">{{ refItem.content }}</div>
                    </el-card>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </div>

        <el-input
          v-model="message"
          type="textarea"
          :rows="3"
          placeholder="输入问题，Enter发送（Shift+Enter换行）"
          style="margin-top:12px"
          @keydown.enter.exact.prevent="send" />
        <el-button type="primary" style="margin-top:8px" :loading="sending" @click="send">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { deleteConversationApi, listConversationMessagesApi, listConversationsApi } from '../api/chat'

const message = ref('')
const deepThinking = ref(false)
const sending = ref(false)
const loadingConversations = ref(false)
const loadingMessages = ref(false)
const historyVisibleCount = ref(10)
const statusText = ref('就绪')
const activeConversationId = ref(sessionStorage.getItem('onerag_conversation_id') || '')
const conversations = ref([])
const messages = ref([])
const pipelineLog = ref('')
const messageContainerRef = ref(null)
const authStore = useAuthStore()
const visibleMessages = computed(() => {
  const total = messages.value.length
  const start = Math.max(0, total - historyVisibleCount.value)
  return messages.value.slice(start)
})
const hasOlderMessages = computed(() => messages.value.length > historyVisibleCount.value)

const parseSseBlocks = (buffer) => {
  const events = []
  let sep = buffer.indexOf('\n\n')
  while (sep !== -1) {
    const raw = buffer.slice(0, sep)
    buffer = buffer.slice(sep + 2)
    let eventName = 'message'
    const dataLines = []
    for (const line of raw.split(/\r?\n/)) {
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
    }
    events.push({ event: eventName, data: dataLines.join('\n') })
    sep = buffer.indexOf('\n\n')
  }
  return { events, rest: buffer }
}

const formatTime = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getMonth() + 1}-${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const formatMessageTime = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`
}

const scrollToBottom = async (smooth = false) => {
  await nextTick()
  if (!messageContainerRef.value) return
  messageContainerRef.value.scrollTo({
    top: messageContainerRef.value.scrollHeight,
    behavior: smooth ? 'smooth' : 'auto'
  })
}

const showOlderHistory = async () => {
  historyVisibleCount.value += 10
  await nextTick()
}

const loadMessages = async (conversationId, silent = false) => {
  if (!conversationId) {
    activeConversationId.value = ''
    sessionStorage.removeItem('onerag_conversation_id')
    messages.value = []
    if (!silent) statusText.value = '已开启新对话'
    return
  }

  loadingMessages.value = true
  if (!silent) statusText.value = '加载历史消息...'
  try {
    const res = await listConversationMessagesApi(conversationId)
    const rows = Array.isArray(res?.data) ? res.data : []
    messages.value = rows.map((item) => ({
      role: item.role,
      content: item.content || '',
      createTime: item.createTime || null,
      reasoning: '',
      references: [],
      loading: false,
      model: ''
    }))
    activeConversationId.value = conversationId
    historyVisibleCount.value = 10
    sessionStorage.setItem('onerag_conversation_id', conversationId)
    pipelineLog.value = ''
    statusText.value = `已加载历史会话（${rows.length} 条消息）`
    await scrollToBottom()
  } catch (e) {
    ElMessage.error('加载历史消息失败')
    statusText.value = '加载历史消息失败'
  } finally {
    loadingMessages.value = false
  }
}

const loadConversations = async (preferredId = activeConversationId.value) => {
  loadingConversations.value = true
  try {
    const res = await listConversationsApi()
    const rows = Array.isArray(res?.data) ? res.data : []
    conversations.value = rows

    let targetId = ''
    if (preferredId && rows.some((item) => item.conversationId === preferredId)) {
      targetId = preferredId
    } else if (rows.length) {
      targetId = rows[0].conversationId
    }

    if (targetId) {
      await loadMessages(targetId, true)
    } else {
      activeConversationId.value = ''
      sessionStorage.removeItem('onerag_conversation_id')
      messages.value = []
      statusText.value = '暂无历史会话'
    }
  } catch (e) {
    ElMessage.error('加载会话列表失败')
    statusText.value = '加载会话列表失败'
  } finally {
    loadingConversations.value = false
  }
}

const selectConversation = async (item) => {
  if (!item?.conversationId || item.conversationId === activeConversationId.value) return
  if (sending.value) {
    ElMessage.warning('正在生成回复，请稍后切换会话')
    return
  }
  await loadMessages(item.conversationId)
}

const newConversation = () => {
  if (sending.value) {
    ElMessage.warning('正在生成回复，请稍后')
    return
  }
  activeConversationId.value = ''
  sessionStorage.removeItem('onerag_conversation_id')
  messages.value = []
  historyVisibleCount.value = 10
  pipelineLog.value = ''
  statusText.value = '已开启新对话'
}

const removeConversation = async (item) => {
  if (!item?.conversationId) return
  if (sending.value) {
    ElMessage.warning('正在生成回复，请稍后删除')
    return
  }
  try {
    await ElMessageBox.confirm('确认删除该会话？删除后不可恢复。', '提示', { type: 'warning' })
    await deleteConversationApi(item.conversationId)
    ElMessage.success('会话已删除')
    const next = conversations.value.find((it) => it.conversationId !== item.conversationId)
    if (item.conversationId === activeConversationId.value) {
      await loadConversations(next?.conversationId || '')
    } else {
      await loadConversations(activeConversationId.value)
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除会话失败')
    }
  }
}

const send = async () => {
  const text = (message.value || '').trim()
  if (!text || sending.value) return
  sending.value = true
  statusText.value = '连接流式接口...'
  message.value = ''

  messages.value.push({ role: 'user', content: text, createTime: new Date().toISOString() })
  const assistant = {
    role: 'assistant',
    content: '',
    createTime: new Date().toISOString(),
    reasoning: '',
    references: [],
    loading: true,
    model: ''
  }
  messages.value.push(assistant)
  historyVisibleCount.value = Math.max(10, historyVisibleCount.value)
  await scrollToBottom(true)

  try {
    const headers = { 'Content-Type': 'application/json', Accept: 'text/event-stream' }
    if (authStore.token) headers.Authorization = authStore.token
    const res = await fetch('/api/chat/stream', {
      method: 'POST',
      headers,
      body: JSON.stringify({
        message: text,
        conversationId: activeConversationId.value || null,
        deepThinking: !!deepThinking.value
      })
    })

    if (!res.ok || !res.body) {
      assistant.loading = false
      assistant.content = `错误: HTTP ${res.status}`
      statusText.value = `失败: HTTP ${res.status}`
      return
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''
    let activeModel = ''
    let doneEvent = false

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      const { events, rest } = parseSseBlocks(buf)
      buf = rest

      for (const { event, data } of events) {
        if (event === 'meta') {
          try {
            const j = JSON.parse(data)
            if (j.conversationId) {
              activeConversationId.value = j.conversationId
              sessionStorage.setItem('onerag_conversation_id', j.conversationId)
            }
            if (j.model) {
              activeModel = j.model
              assistant.model = j.model
            }
            pipelineLog.value += `meta: ${JSON.stringify(j)}\n\n`
          } catch (e) {
            // ignore
          }
        } else if (event === 'queryRewrite') {
          pipelineLog.value += `改写: ${data}\n\n`
        } else if (event === 'intent') {
          pipelineLog.value += `意图: ${data}\n\n`
        } else if (event === 'first-token') {
          statusText.value = `生成中... ${data}${activeModel ? ` · 模型=${activeModel}` : ''}`
        } else if (event === 'reasoning') {
          assistant.reasoning += data
          await scrollToBottom()
        } else if (event === 'reasoning-reset') {
          assistant.reasoning = data || ''
          await scrollToBottom()
        } else if (event === 'references') {
          try {
            const payload = JSON.parse(data)
            const items = Array.isArray(payload.items) ? payload.items : []
            assistant.references = items.map((it) => ({
              index: it.index,
              title: it.title,
              docId: it.docId,
              score: Number(it.score).toFixed(3),
              content: it.content
            }))
          } catch (e) {
            // ignore
          }
        } else if (event === 'content') {
          assistant.content += data
          await scrollToBottom()
        } else if (event === 'error') {
          assistant.loading = false
          assistant.content = assistant.content || `错误: ${data}`
          statusText.value = '流式错误'
          await scrollToBottom()
        } else if (event === 'done') {
          doneEvent = true
          assistant.loading = false
          statusText.value = `完成${activeConversationId.value ? ` · conversationId=${activeConversationId.value}` : ''}${activeModel ? ` · 模型=${activeModel}` : ''}`
          await scrollToBottom()
        }
      }
    }

    assistant.loading = false
    if (!assistant.content) {
      assistant.content = '未生成可展示答案，请重试。'
    }
    if (!doneEvent) {
      statusText.value = '连接已结束，未收到 done 事件'
    }
  } catch (e) {
    assistant.loading = false
    assistant.content = `错误: ${String(e.message || e)}`
    statusText.value = '发送失败'
    ElMessage.error('发送失败')
  } finally {
    sending.value = false
    await loadConversations(activeConversationId.value)
  }
}

onMounted(async () => {
  await loadConversations(activeConversationId.value)
})
</script>

<style scoped>
.chat-layout {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

.chat-sidebar {
  width: 280px;
  flex-shrink: 0;
}

.chat-sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chat-conversation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.chat-conversation-item {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  cursor: pointer;
}

.chat-conversation-item.active {
  border-color: #409eff;
  background: #f5f9ff;
}

.chat-sidebar-actions {
  margin-top: 4px;
}

.chat-conversation-main {
  min-width: 0;
}

.chat-conversation-title {
  font-size: 14px;
  line-height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-conversation-time {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.chat-content {
  flex: 1;
  min-width: 0;
}

.pipeline-floating {
  position: sticky;
  top: 0;
  z-index: 2;
}

.pipeline-log {
  margin: 0;
  max-height: 140px;
  overflow: auto;
  white-space: pre-wrap;
}

.chat-message-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: calc(100vh - 360px);
  overflow: auto;
  padding-right: 4px;
}

.chat-empty {
  color: #909399;
  padding: 8px 0;
}

.history-toggle {
  display: flex;
  justify-content: center;
}

.message-row {
  display: flex;
}

.message-row.assistant {
  justify-content: flex-start;
}

.message-row.user {
  justify-content: flex-end;
}

.message-bubble {
  max-width: 70%;
  border-radius: 10px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

.message-row.user .message-bubble {
  background: #eaf4ff;
  border-color: #bcdfff;
}

.message-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  gap: 12px;
}

.message-model {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
</style>
