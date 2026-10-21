<template>
  <section class="discussion-panel">
    <div class="panel-head">
      <strong>项目讨论</strong>
      <div>
        <el-button size="small" :loading="loading" @click="$emit('refresh')">刷新</el-button>
        <el-button size="small" type="primary" :loading="coordinating" @click="onCoordinate">协调摘要</el-button>
      </div>
    </div>
    <p class="hint">项目成员可见的共享讨论。协调摘要只读生成风险/问题/任务草稿，不会自动执行。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-alert v-if="coordinator" type="success" :closable="false" show-icon class="coord">
      <template #title>协调摘要</template>
      <p>{{ coordinator.summary }}</p>
      <ul v-if="coordinator.risks?.length">
        <li v-for="(risk, i) in coordinator.risks" :key="'r' + i">风险：{{ risk }}</li>
      </ul>
      <ul v-if="coordinator.questions?.length">
        <li v-for="(q, i) in coordinator.questions" :key="'q' + i">问题：{{ q }}</li>
      </ul>
      <p v-if="coordinator.taskDrafts?.length">已建议 {{ coordinator.taskDrafts.length }} 条任务草稿，可在任务草稿区确认。</p>
    </el-alert>
    <div class="messages" v-loading="loading">
      <el-empty v-if="!loading && messages.length === 0" description="暂无讨论" :image-size="64" />
      <article v-for="msg in messages" :key="msg.id" class="msg">
        <header>
          <strong>{{ msg.senderName || msg.senderType || '成员' }}</strong>
          <span class="time">{{ formatTime(msg.createTime) }}</span>
        </header>
        <p>{{ msg.content }}</p>
      </article>
    </div>
    <div class="composer">
      <el-input v-model="draft" type="textarea" :rows="2" maxlength="2000" placeholder="输入讨论内容" :disabled="busy" />
      <el-button type="primary" :loading="busy" :disabled="!draft.trim()" @click="submit">发送</el-button>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  projectKey: { type: String, default: '' },
  messages: { type: Array, default: () => [] },
  loading: Boolean,
  busy: Boolean,
  coordinating: Boolean,
  error: { type: String, default: '' },
  coordinator: { type: Object, default: null }
})
const emit = defineEmits(['refresh', 'post', 'coordinate'])
const draft = ref('')

function formatTime(value) {
  if (!value) return ''
  try {
    return new Date(value).toLocaleString()
  } catch {
    return String(value)
  }
}

function submit() {
  const content = draft.value.trim()
  if (!content) return
  emit('post', content)
  draft.value = ''
}

function onCoordinate() {
  emit('coordinate')
}
</script>

<style scoped>
.discussion-panel {
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  padding: 12px 14px;
  background: var(--el-bg-color);
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin: 0 0 8px;
}
.messages {
  max-height: 280px;
  overflow: auto;
  margin-bottom: 8px;
}
.msg {
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.msg header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.msg p {
  margin: 4px 0 0;
  white-space: pre-wrap;
}
.composer {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.coord {
  margin-bottom: 8px;
}
</style>
