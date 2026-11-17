<template>
  <div class="ai-assistant" :class="{ minimized }">
    <header class="ai-header" @click="minimized = !minimized">
      <span class="ai-icon">🤖</span>
      <span class="ai-title">AI助手</span>
      <span class="toggle-icon">{{ minimized ? '⌃' : '⌄' }}</span>
    </header>

    <div v-if="!minimized" class="ai-body">
      <div class="messages">
        <div
          v-for="(message, index) in messages"
          :key="index"
          class="message"
          :class="message.type === 'user' ? 'user-message' : 'ai-message'"
        >
          <div class="message-header">
            <span>{{ message.type === 'user' ? '👤 你' : '🤖 AI' }}</span>
          </div>
          <div class="message-content">{{ message.content }}</div>
          <div v-if="message.contextInfo" class="message-context">{{ message.contextInfo }}</div>
        </div>

        <div v-if="loading" class="loading-indicator"><span class="dot"></span>正在思考…</div>
      </div>

      <div class="input-area">
        <input
          v-model="question"
          placeholder="输入问题…"
          @keyup.enter="send"
        />
        <button class="send-btn" :disabled="!question.trim() || loading" @click="send">发送</button>
      </div>

      <button class="clear-btn" @click="clear">清空对话</button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { askAssistant } from '@/api/ml'

const props = defineProps({
  context: { type: String, default: '' }
})

const minimized = ref(true)
const question = ref('')
const loading = ref(false)
const messages = ref([])

async function send() {
  const text = question.value.trim()
  if (!text || loading.value) return

  messages.value.push({ type: 'user', content: text })
  question.value = ''
  loading.value = true
  try {
    const response = await askAssistant({ question: text, context: props.context })
    messages.value.push({
      type: 'ai',
      content: response.data?.answer ?? '（无内容）',
      contextInfo: props.context
    })
  } catch (error) {
    messages.value.push({ type: 'ai', content: '请求失败：' + error.message })
  } finally {
    loading.value = false
  }
}

function clear() {
  messages.value = []
}

onMounted(() => {
  messages.value.push({
    type: 'ai',
    content: '你好，我是机器学习助教。可以问我算法原理、参数怎么调，或当前图表怎么看。'
  })
})
</script>

<style scoped>
.ai-assistant {
  position: fixed;
  right: 26px;
  bottom: 22px;
  width: 320px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 18px;
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.18);
  overflow: hidden;
  z-index: 40;
}

.ai-assistant.minimized {
  width: 200px;
}

.ai-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 14px;
  cursor: pointer;
  color: #fff;
  background: linear-gradient(120deg, #0891b2, #7c3aed);
}

.ai-title {
  font-size: 13px;
  font-weight: 700;
}

.toggle-icon {
  margin-left: auto;
  font-size: 12px;
}

.ai-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
}

.messages {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
}

.message {
  padding: 8px 10px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.6;
}

.user-message {
  align-self: flex-end;
  background: #eef4ff;
  color: #1e3a8a;
}

.ai-message {
  background: #f8fafc;
  color: #334155;
}

.message-header {
  font-size: 10px;
  color: #94a3b8;
  margin-bottom: 3px;
}

.message-context {
  margin-top: 4px;
  font-size: 10px;
  color: #94a3b8;
}

.loading-indicator {
  font-size: 11px;
  color: #64748b;
}

.input-area {
  display: flex;
  gap: 6px;
}

.input-area input {
  flex: 1;
  padding: 7px 10px;
  font-size: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 9px;
  outline: none;
}

.send-btn {
  padding: 7px 12px;
  font-size: 12px;
  color: #fff;
  background: linear-gradient(120deg, #0891b2, #7c3aed);
  border: none;
  border-radius: 9px;
  cursor: pointer;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.clear-btn {
  font-size: 11px;
  color: #64748b;
  background: none;
  border: none;
  cursor: pointer;
  align-self: flex-start;
}
</style>
