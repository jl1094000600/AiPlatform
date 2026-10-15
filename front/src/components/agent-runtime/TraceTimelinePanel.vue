<template>
  <div class="trace-panel">
    <div class="panel-toolbar">
      <span class="hint">Span 时间线（按开始时间）</span>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert
      v-if="mocked"
      title="开发 mock：当前展示演示数据。仅在开发环境或 VITE_AGENT_RUNTIME_MOCK=1 时启用；正式运行请求失败将直接报错，不会静默降级。"
      type="warning"
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
        :key="spanKey(span)"
        :timestamp="formatTime(spanStartedAt(span))"
        :type="spanDotType(span.status)"
        placement="top"
      >
        <div class="span-card">
          <div class="span-head">
            <el-tag size="small" effect="plain">{{ typeLabel(span.spanType ?? span.type) }}</el-tag>
            <el-tag :type="statusType(span.status)" size="small">{{ statusLabel(span.status) }}</el-tag>
            <span class="span-duration">{{ formatDuration(spanDurationMs(span)) }}</span>
          </div>
          <p class="span-detail">{{ spanDetail(span) || '-' }}</p>
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
import {
  normalizeSpans,
  spanStatusLabel,
  spanStatusType,
  spanTypeLabel
} from '../../api/agentRuntimeContract'

const props = defineProps({
  runId: { type: [String, Number], required: true }
})

const loading = ref(false)
const spans = ref([])
const mocked = ref(false)
const forbidden = ref(false)
const error = ref('')

// Dual-enum compatible: backend spanType/startTime/summary/spanId + legacy type/startedAt/detail/id
const typeLabel = (type) => spanTypeLabel(type)
const statusLabel = (status) => spanStatusLabel(status)
const statusType = (status) => spanStatusType(status)
const spanDotType = (status) => spanStatusType(status)

const spanKey = (span) => span?.spanId ?? span?.id
const spanStartedAt = (span) => span?.startTime ?? span?.startedAt
const spanDurationMs = (span) => span?.durationMs
const spanDetail = (span) => span?.summary ?? span?.detail ?? span?.spanName

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
    spans.value = normalizeSpans(payload)
    spans.value = [...spans.value].sort((a, b) => new Date(spanStartedAt(a) || 0) - new Date(spanStartedAt(b) || 0))
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
