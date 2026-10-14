<template>
  <div class="trace-panel">
    <div class="panel-toolbar">
      <span class="hint">Span 时间线（按开始时间）</span>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert
      v-if="mocked"
      title="后端接口未就绪，当前展示演示数据。接口就绪后将自动切换为真实数据。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-alert
      v-if="forbidden"
      title="你没有查看该运行 Trace 的权限"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-alert
      v-else-if="error && !mocked"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    />

    <el-timeline v-if="!forbidden && spans.length" class="trace-timeline">
      <el-timeline-item
        v-for="span in spans"
        :key="span.id"
        :timestamp="formatTime(span.startedAt)"
        :type="spanType(span.status)"
        placement="top"
      >
        <div class="span-card">
          <div class="span-head">
            <el-tag size="small" effect="plain">{{ typeLabel(span.type) }}</el-tag>
            <el-tag :type="statusType(span.status)" size="small">{{ statusLabel(span.status) }}</el-tag>
            <span class="span-duration">{{ formatDuration(span.durationMs) }}</span>
          </div>
          <p class="span-detail">{{ span.detail || '-' }}</p>
        </div>
      </el-timeline-item>
    </el-timeline>

    <el-empty
      v-if="!loading && !forbidden && !error && spans.length === 0"
      description="暂无 Trace 数据"
      :image-size="72"
    />
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import agentRuntime from '../../api/agentRuntime'

const props = defineProps({
  runId: { type: [String, Number], required: true }
})

const loading = ref(false)
const spans = ref([])
const mocked = ref(false)
const forbidden = ref(false)
const error = ref('')

const typeLabel = (type) => ({
  run: '运行',
  llm: '模型',
  tool: '工具',
  ask_user: '用户交互',
  budget: '预算',
  workspace: '沙箱',
  pipeline: '流水线'
})[type] || type || '-'

const statusLabel = (status) => ({
  SUCCESS: '成功',
  SUCCEEDED: '成功',
  FAILED: '失败',
  RUNNING: '进行中',
  WAITING_USER: '待回复',
  TIMEOUT: '超时',
  CANCELLED: '已取消',
  PENDING: '待执行'
})[status] || status || '-'

const statusType = (status) => ({
  SUCCESS: 'success',
  SUCCEEDED: 'success',
  FAILED: 'danger',
  RUNNING: 'primary',
  WAITING_USER: 'warning',
  TIMEOUT: 'danger',
  CANCELLED: 'info',
  PENDING: 'info'
})[status] || 'info'

const spanType = (status) => ({
  SUCCESS: 'success',
  SUCCEEDED: 'success',
  FAILED: 'danger',
  RUNNING: 'primary',
  WAITING_USER: 'warning',
  TIMEOUT: 'danger'
})[status] || 'info'

const formatTime = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const formatDuration = (ms) => {
  if (ms === null || ms === undefined || ms === '') return '-'
  const n = Number(ms)
  if (!Number.isFinite(n)) return '-'
  return n >= 1000 ? (n / 1000).toFixed(2) + 's' : n + 'ms'
}

const load = async () => {
  if (!props.runId) return
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const response = await agentRuntime.getAgentRunTrace(props.runId)
    const payload = response?.data?.data
    spans.value = Array.isArray(payload) ? payload : (payload?.spans || payload?.records || [])
    spans.value = [...spans.value].sort((a, b) => new Date(a.startedAt || 0) - new Date(b.startedAt || 0))
    mocked.value = Boolean(response?.data?.mock)
  } catch (err) {
    if (agentRuntime.isForbidden(err)) {
      forbidden.value = true
      spans.value = []
    } else {
      error.value = 'Trace 加载失败，请稍后重试'
      spans.value = []
    }
  } finally {
    loading.value = false
  }
}

watch(() => props.runId, () => { spans.value = []; load() })
onMounted(load)
</script>

<style scoped>
.trace-panel { display: flex; flex-direction: column; gap: 12px; }
.panel-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.hint { color: var(--text-muted); font-size: 12px; }
.trace-timeline { padding-left: 4px; }
.span-card {
  border: 1px solid var(--glass-border);
  border-radius: 8px;
  padding: 10px 12px;
  background: rgba(148, 163, 184, .06);
}
.span-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.span-duration { margin-left: auto; font-family: var(--font-mono); font-size: 12px; color: var(--text-muted); }
.span-detail { margin: 0; font-size: 13px; line-height: 1.55; color: var(--text-secondary); word-break: break-word; }
</style>
