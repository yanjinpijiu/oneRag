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
        <div class="chat-flow-layer" aria-hidden="true"></div>
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
          <div v-else-if="!messages.length" class="chat-empty">
            <div class="chat-empty-title">开始新对话</div>
            <div class="chat-empty-desc">你可以先试试这些常见问题：</div>
            <div class="chat-suggestions">
              <el-tag
                v-for="q in starterQuestions"
                :key="q"
                class="chat-suggestion-tag"
                type="info"
                effect="plain"
                @click="useStarterQuestion(q)">
                {{ q }}
              </el-tag>
            </div>
          </div>
          <div v-else-if="hasOlderMessages" class="history-toggle">
            <el-button size="small" plain @click="showOlderHistory">显示更早历史对话（10条）</el-button>
          </div>
          <transition-group name="message-fade" tag="div" class="message-group">
            <div
              v-for="(item, idx) in visibleMessages"
              :key="`${idx}-${item.role}`"
              :class="['message-row', item.role === 'user' ? 'user' : 'assistant']">
              <div class="message-bubble">
                <div class="message-meta">
                  <span>{{ item.role === 'user' ? '我' : '助手' }}</span>
                  <span>{{ formatMessageTime(item.createTime) }}</span>
                </div>
                <el-skeleton v-if="item.loading && !item.content && !item.reasoning && !(item._streaming && (streamingReasoningText || streamingContentText))" :rows="2" animated />
                <div v-if="item.loading && item._streaming && streamingReasoningText" class="reasoning-live">
                  <div class="reasoning-live-title">思考过程（流式）</div>
                  <div style="white-space:pre-wrap">{{ streamingReasoningText }}</div>
                </div>
                <el-collapse v-else-if="item.reasoning">
                  <el-collapse-item title="思考过程">
                    <div style="white-space:pre-wrap">{{ item.reasoning }}</div>
                  </el-collapse-item>
                </el-collapse>
                <div :class="['content-text', { streaming: item.loading && item._streaming }]">{{ item.loading && item._streaming ? streamingContentText : item.content }}</div>
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
          </transition-group>
        </div>

        <el-input
          v-model="message"
          type="textarea"
          :rows="3"
          placeholder="输入问题，Enter发送（Shift+Enter换行）"
          style="margin-top:12px"
          @keydown.enter.exact.prevent="send" />
        <el-button type="primary" class="send-btn" :loading="sending" @click="send">发送</el-button>
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
const streamingReasoningText = ref('')
const streamingContentText = ref('')
const starterQuestions = [
  '你好，你是谁？',
  '你能帮我做什么？',
  '奖学金申请条件是什么？',
  '毕业设计流程怎么安排？',
  '图书馆开放时间是几点？'
]
const authStore = useAuthStore()
const visibleMessages = computed(() => {
  const total = messages.value.length
  const start = Math.max(0, total - historyVisibleCount.value)
  return messages.value.slice(start)
})
const hasOlderMessages = computed(() => messages.value.length > historyVisibleCount.value)

const debugLog = (runId, hypothesisId, message, data) => {
  const noisy = message === 'stream.chunk.read' ||
    message === 'stream.events.parsed' ||
    message === 'stream.event' ||
    message === 'assistant.reasoning.append' ||
    message === 'assistant.content.append'
  if (noisy) return
  fetch('http://127.0.0.1:7585/ingest/62381010-e7a9-4d77-86d2-e52e60c5476f',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'3170f1'},body:JSON.stringify({sessionId:'3170f1',runId,hypothesisId,location:'frontend/src/views/ChatView.vue',message,data,timestamp:Date.now()})}).catch(()=>{})
}

const parseSseBlocks = (buffer) => {
  const events = []
  let sepMatch = buffer.match(/\r?\n\r?\n/)
  while (sepMatch && sepMatch.index !== undefined) {
    const sep = sepMatch.index
    const raw = buffer.slice(0, sep)
    buffer = buffer.slice(sep + sepMatch[0].length)
    let eventName = 'message'
    const dataLines = []
    for (const line of raw.split(/\r?\n/)) {
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
    }
    events.push({ event: eventName, data: dataLines.join('\n') })
    sepMatch = buffer.match(/\r?\n\r?\n/)
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

let scrollScheduled = false
const scheduleScrollToBottom = () => {
  if (scrollScheduled) return
  scrollScheduled = true
  requestAnimationFrame(() => {
    scrollScheduled = false
    scrollToBottom()
  })
}

const showOlderHistory = async () => {
  historyVisibleCount.value += 10
  await nextTick()
}

const useStarterQuestion = (text) => {
  if (sending.value) return
  message.value = text
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

const refreshConversationsOnly = async () => {
  try {
    const res = await listConversationsApi()
    const rows = Array.isArray(res?.data) ? res.data : []
    conversations.value = rows
    if (activeConversationId.value && !rows.some((item) => item.conversationId === activeConversationId.value)) {
      activeConversationId.value = ''
      sessionStorage.removeItem('onerag_conversation_id')
    }
  } catch (e) {
    // ignore refresh failures here; main flow already completed
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
  // #region agent log
  debugLog('run3', 'H1', 'send.start', {
    deepThinking: !!deepThinking.value,
    hasConversationId: !!activeConversationId.value,
    textLen: text.length
  })
  // #endregion

  messages.value.push({ role: 'user', content: text, createTime: new Date().toISOString() })
  const assistant = {
    role: 'assistant',
    content: '',
    createTime: new Date().toISOString(),
    reasoning: '',
    references: [],
    loading: true,
    model: '',
    _streaming: true
  }
  messages.value.push(assistant)
  streamingReasoningText.value = ''
  streamingContentText.value = ''
  historyVisibleCount.value = Math.max(10, historyVisibleCount.value)
  await scrollToBottom(true)
  const clientStats = {
    runId: 'run9',
    readCount: 0,
    eventCount: 0,
    maxEventsPerRead: 0,
    firstReadDelayMs: -1,
    firstEventDelayMs: -1,
    firstContentDelayMs: -1,
    firstReasoningDelayMs: -1,
    contentEventCount: 0,
    reasoningEventCount: 0
  }
  const clientStartAt = Date.now()

  try {
    const prevClientStats = sessionStorage.getItem('onerag_prev_client_stats') || ''
    const headers = {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      'X-Debug-Run-Id': 'run9',
      'X-Debug-Prev-Client-Stats': prevClientStats
    }
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
    // #region agent log
    debugLog('run3', 'H2', 'stream.response', {
      ok: res.ok,
      status: res.status,
      hasBody: !!res.body
    })
    // #endregion

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
    let gotStreamingToken = false
    let pendingReasoning = ''
    let pendingContent = ''
    let flushScheduled = false
    let firstReasoningEventLogged = false
    let firstContentEventLogged = false
    let firstFlushLogged = false
    const eventTypeCounts = {}
    const firstEvents = []
    const flushPending = () => {
      flushScheduled = false
      let changed = false
      let flushReasoningLen = 0
      let flushContentLen = 0
      if (pendingReasoning) {
        flushReasoningLen = pendingReasoning.length
        streamingReasoningText.value += pendingReasoning
        pendingReasoning = ''
        changed = true
      }
      if (pendingContent) {
        flushContentLen = pendingContent.length
        streamingContentText.value += pendingContent
        pendingContent = ''
        changed = true
      }
      if (changed) {
        if (!firstFlushLogged) {
          firstFlushLogged = true
          debugLog('run9', 'H11', 'stream.first.flush', {
            atMs: Date.now() - clientStartAt,
            flushReasoningLen,
            flushContentLen,
            reasoningLen: streamingReasoningText.value.length,
            contentLen: streamingContentText.value.length
          })
        }
        scheduleScrollToBottom()
      }
    }
    const scheduleFlush = () => {
      if (flushScheduled) return
      flushScheduled = true
      requestAnimationFrame(flushPending)
    }
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      clientStats.readCount += 1
      if (clientStats.firstReadDelayMs < 0) {
        clientStats.firstReadDelayMs = Date.now() - clientStartAt
      }
      // #region agent log
      debugLog('run3', 'H2', 'stream.chunk.read', { bytes: value?.length || 0 })
      // #endregion
      buf += decoder.decode(value, { stream: true })
      const { events, rest } = parseSseBlocks(buf)
      buf = rest
      // #region agent log
      debugLog('run3', 'H2', 'stream.events.parsed', {
        eventCount: events.length,
        restLen: rest.length
      })
      // #endregion
      clientStats.eventCount += events.length
      if (events.length > clientStats.maxEventsPerRead) {
        clientStats.maxEventsPerRead = events.length
      }
      if (clientStats.firstEventDelayMs < 0 && events.length > 0) {
        clientStats.firstEventDelayMs = Date.now() - clientStartAt
      }

      for (const { event, data } of events) {
        eventTypeCounts[event] = (eventTypeCounts[event] || 0) + 1
        if (firstEvents.length < 10) {
          firstEvents.push({ event, dataLen: (data || '').length })
        }
        // #region agent log
        debugLog('run3', 'H3', 'stream.event', {
          event,
          dataLen: (data || '').length
        })
        // #endregion
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
          gotStreamingToken = true
          clientStats.reasoningEventCount += 1
          if (clientStats.firstReasoningDelayMs < 0) {
            clientStats.firstReasoningDelayMs = Date.now() - clientStartAt
          }
          if (!firstReasoningEventLogged) {
            firstReasoningEventLogged = true
            debugLog('run9', 'H11', 'stream.first.reasoning.event', {
              atMs: Date.now() - clientStartAt,
              dataLen: (data || '').length
            })
          }
          pendingReasoning += data
          scheduleFlush()
          // #region agent log
          debugLog('run3', 'H4', 'assistant.reasoning.append', {
            reasoningLen: streamingReasoningText.value.length + pendingReasoning.length,
            contentLen: streamingContentText.value.length + pendingContent.length
          })
          // #endregion
        } else if (event === 'reasoning-reset') {
          pendingReasoning = ''
          streamingReasoningText.value = data || ''
          // #region agent log
          debugLog('run3', 'H4', 'assistant.reasoning.reset', {
            reasoningLen: streamingReasoningText.value.length
          })
          // #endregion
          scheduleScrollToBottom()
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
          gotStreamingToken = true
          clientStats.contentEventCount += 1
          if (clientStats.firstContentDelayMs < 0) {
            clientStats.firstContentDelayMs = Date.now() - clientStartAt
          }
          if (!firstContentEventLogged) {
            firstContentEventLogged = true
            debugLog('run9', 'H11', 'stream.first.content.event', {
              atMs: Date.now() - clientStartAt,
              dataLen: (data || '').length
            })
          }
          pendingContent += data
          scheduleFlush()
          // #region agent log
          debugLog('run3', 'H4', 'assistant.content.append', {
            reasoningLen: streamingReasoningText.value.length + pendingReasoning.length,
            contentLen: streamingContentText.value.length + pendingContent.length
          })
          // #endregion
        } else if (event === 'error') {
          if (flushScheduled) flushPending()
          assistant.loading = false
          assistant.reasoning = streamingReasoningText.value
          assistant.content = streamingContentText.value || `错误: ${data}`
          assistant._streaming = false
          statusText.value = '流式错误'
          await scrollToBottom()
        } else if (event === 'done') {
          if (flushScheduled) flushPending()
          doneEvent = true
          assistant.loading = false
          assistant.reasoning = streamingReasoningText.value
          assistant.content = streamingContentText.value
          assistant._streaming = false
          statusText.value = `完成${activeConversationId.value ? ` · conversationId=${activeConversationId.value}` : ''}${activeModel ? ` · 模型=${activeModel}` : ''}`
          // #region agent log
          debugLog('run3', 'H5', 'stream.done', {
            reasoningLen: streamingReasoningText.value.length,
            contentLen: streamingContentText.value.length
          })
          // #endregion
          await scrollToBottom()
        }
      }
    }

    if (flushScheduled) flushPending()
    assistant.loading = false
    if (assistant._streaming) {
      assistant.reasoning = streamingReasoningText.value
      assistant.content = streamingContentText.value
      assistant._streaming = false
    }
    if (!assistant.content && !assistant.reasoning) {
      assistant.content = '未生成可展示答案，请重试。'
    }
    if (!doneEvent) {
      statusText.value = gotStreamingToken ? '连接已结束' : '连接已结束，未收到有效流式内容'
    }
    // #region agent log
    debugLog('run3', 'H5', 'send.final', {
      doneEvent,
      gotStreamingToken,
      reasoningLen: assistant.reasoning.length,
      contentLen: assistant.content.length
    })
    debugLog('run9', 'H11', 'stream.event.summary', {
      eventTypeCounts,
      firstEvents,
      firstReasoningEventLogged,
      firstContentEventLogged
    })
    // #endregion
  } catch (e) {
    assistant.loading = false
    assistant.content = `错误: ${String(e.message || e)}`
    statusText.value = '发送失败'
    ElMessage.error('发送失败')
  } finally {
    sessionStorage.setItem('onerag_prev_client_stats', JSON.stringify(clientStats))
    sending.value = false
    // #region agent log
    debugLog('run3', 'H1', 'send.finally.refreshConversationsOnly', {
      activeConversationId: activeConversationId.value || ''
    })
    // #endregion
    await refreshConversationsOnly()
  }
}

onMounted(async () => {
  await loadConversations(activeConversationId.value)
})
</script>

<style scoped>
.chat-layout {
  display: flex;
  gap: 14px;
  align-items: stretch;
}

.chat-sidebar {
  width: 280px;
  flex-shrink: 0;
  border-radius: 14px;
  border: 1px solid var(--glass-border);
  background: var(--glass-bg);
  backdrop-filter: blur(10px);
  box-shadow: var(--glass-shadow);
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
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  padding: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  cursor: pointer;
  transition: all 0.22s ease;
}

.chat-conversation-item:hover {
  transform: translateY(-1px);
  border-color: #d4def2;
  box-shadow: 0 6px 14px rgba(31, 42, 68, 0.06);
}

.chat-conversation-item.active {
  border-color: var(--brand);
  background: linear-gradient(135deg, rgba(234, 240, 255, 0.85) 0%, rgba(243, 247, 255, 0.82) 100%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.75),
    0 8px 18px rgba(79, 124, 255, 0.16);
  backdrop-filter: blur(8px);
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
  position: relative;
}

.chat-flow-layer {
  position: absolute;
  inset: 0;
  border-radius: 14px;
  pointer-events: none;
  background:
    radial-gradient(circle at 12% 18%, rgba(79, 124, 255, 0.09) 0%, rgba(79, 124, 255, 0) 50%),
    radial-gradient(circle at 88% 22%, rgba(119, 151, 255, 0.08) 0%, rgba(119, 151, 255, 0) 52%),
    radial-gradient(circle at 50% 94%, rgba(103, 173, 255, 0.07) 0%, rgba(103, 173, 255, 0) 48%);
  animation: chatBgFlow 18s ease-in-out infinite alternate;
  z-index: 0;
}

.chat-content > * {
  position: relative;
  z-index: 1;
}

.pipeline-floating {
  position: sticky;
  top: 0;
  z-index: 2;
  border-radius: 14px;
  border: 1px solid var(--glass-border);
  background: var(--glass-bg);
  backdrop-filter: blur(10px);
  box-shadow: var(--glass-shadow);
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
  color: var(--text-secondary);
  padding: 8px 0 12px;
}

.chat-empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.chat-empty-desc {
  margin-top: 6px;
  color: var(--text-regular);
}

.chat-suggestions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.chat-suggestion-tag {
  cursor: pointer;
  transition: all 0.2s ease;
}

.chat-suggestion-tag:hover {
  color: var(--brand);
  border-color: #cfdcff;
  background: var(--brand-soft);
}

.history-toggle {
  display: flex;
  justify-content: center;
}

.message-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
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
  border-radius: 12px;
  padding: 10px 12px;
  border: 1px solid var(--glass-border);
  background: rgba(255, 255, 255, 0.76);
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 18px rgba(31, 42, 68, 0.08);
  position: relative;
  overflow: hidden;
}

.message-bubble::before {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.58) 0%, rgba(255, 255, 255, 0) 36%),
    radial-gradient(circle at 12% 10%, rgba(255, 255, 255, 0.56) 0%, rgba(255, 255, 255, 0) 40%);
  opacity: 0.86;
}

.message-bubble::after {
  content: "";
  position: absolute;
  inset: 1px;
  border-radius: 11px;
  pointer-events: none;
  border: 1px solid rgba(255, 255, 255, 0.46);
  mix-blend-mode: screen;
}

.message-row.user .message-bubble {
  background: rgba(237, 243, 255, 0.84);
  border-color: #d6e3ff;
}

.message-row.user .message-bubble::before {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.52) 0%, rgba(255, 255, 255, 0) 34%),
    radial-gradient(circle at 88% 12%, rgba(255, 255, 255, 0.5) 0%, rgba(255, 255, 255, 0) 42%);
}

.message-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  gap: 12px;
}

.reasoning-live {
  margin-top: 6px;
  border-left: 3px solid #86a9ff;
  background: #f4f7ff;
  padding: 8px;
  border-radius: 6px;
}

.reasoning-live-title {
  font-size: 12px;
  color: #5f7fd9;
  margin-bottom: 4px;
}

.message-model {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.send-btn {
  margin-top: 8px;
  width: 104px;
  border-radius: 10px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.send-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 16px rgba(79, 124, 255, 0.22);
}

.message-fade-enter-active,
.message-fade-leave-active {
  transition: all 0.22s ease;
}

.message-fade-enter-from,
.message-fade-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

.content-text {
  margin-top: 6px;
  white-space: pre-wrap;
  position: relative;
}

.content-text.streaming::after {
  content: "";
  display: inline-block;
  width: 7px;
  height: 14px;
  margin-left: 4px;
  vertical-align: text-bottom;
  background: rgba(79, 124, 255, 0.78);
  border-radius: 2px;
  animation: typingCursor 0.9s steps(1) infinite;
}

@keyframes typingCursor {
  0%, 45% {
    opacity: 1;
  }
  46%, 100% {
    opacity: 0;
  }
}

@keyframes chatBgFlow {
  0% {
    transform: translate3d(-0.8%, -0.5%, 0) scale(1);
  }
  100% {
    transform: translate3d(0.8%, 0.6%, 0) scale(1.02);
  }
}
</style>
