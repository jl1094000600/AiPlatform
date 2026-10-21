<template>
  <section class="private-ask-panel">
    <div class="panel-head">
      <strong>私密问答</strong>
      <el-button size="small" @click="clear">清空会话</el-button>
    </div>
    <p class="hint">仅本人可见；回答不落库。可将最后一条问答转为任务草稿再确认执行。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div class="thread">
      <el-empty v-if="thread.length === 0" description="向助手提问以开始" :image-size="64" />
      <div v-for="(turn, i) in thread" :key="i" class="turn" :class="turn.role">
        <strong>{{ turn.role === 'user' ? '我' : '助手' }}</strong>
        <p>{{ turn.content }}</p>
      </div>
    </div>
    <div class="composer">
      <el-input v-model="question" type="textarea" :rows="2" maxlength="2000" placeholder="输入问题（不写入共享讨论）" :disabled="busy" />
      <el-button type="primary" :loading="busy" :disabled="!question.trim()" @click="ask">提问</el-button>
      <el-button :disabled="!lastAnswer" :loading="converting" @click="convert">转任务草稿</el-button>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  projectKey: { type: String, default: '' },
  busy: Boolean,
  converting: Boolean,
  error: { type: String, default: '' },
  sources: { type: Array, default: () => [] },
  limitations: { type: Array, default: () => [] }
})
const emit = defineEmits(['ask', 'convert', 'clear'])
const question = ref('')
const thread = ref([])

const lastAnswer = computed(() => {
  for (let i = thread.value.length - 1; i >= 0; i--) {
    if (thread.value[i].role === 'assistant') return thread.value[i].content
  }
  return ''
})

function ask() {
  const q = question.value.trim()
  if (!q) return
  thread.value.push({ role: 'user', content: q })
  question.value = ''
  emit('ask', { question: q, history: thread.value.filter((t) => t.role !== 'assistant' || true) })
}

function pushAnswer(answer) {
  thread.value.push({ role: 'assistant', content: answer })
}

function convert() {
  const answer = lastAnswer.value
  if (!answer) return
  emit('convert', { answer, history: thread.value })
}

function clear() {
  thread.value = []
  emit('clear')
}

defineExpose({ pushAnswer, thread })
</script>

<style scoped>
.private-ask-panel {
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  padding: 12px 14px;
  background: var(--el-bg-color);
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin: 6px 0 8px;
}
.thread {
  max-height: 260px;
  overflow: auto;
  margin-bottom: 8px;
}
.turn {
  padding: 8px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}
.turn.user strong {
  color: var(--el-color-primary);
}
.turn p {
  margin: 4px 0 0;
  white-space: pre-wrap;
}
.composer {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  flex-wrap: wrap;
}
</style>
