<template>
  <div class="tool-calls-panel">
    <div class="panel-toolbar">
      <span class="hint">风险色标：读取 / 写入 / 执行</span>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert
      v-if="mocked"
      title="后端接口未就绪，当前展示演示数据。接口就绪后将自动切换为真实数据。"
      type="info"
      :closable="false"
      show-icon
      class="mock-alert"
    />
    <el-alert
      v-if="forbidden"
      title="你没有查看该运行工具调用的权限"
      type="warning"
      :closable="false"
      show-icon
      class="mock-alert"
    />
    <el-alert
      v-else-if="error && !mocked"
      :title="error"
      type="error"
      :closable="false"
      show-icon
      class="mock-alert"
    />

    <template v-if="!forbidden">
      <div v-if="askPending.length" class="ask-block">
        <h4>待回复（ask_user）</h4>
        <div v-for="item in askPending" :key="item.id" class="ask-card">
          <div class="ask-head">
            <el-tag type="warning" size="small">待用户回复</el-tag>
            <span class="ask-id">{{ item.id }}</span>
            <span v-if="item.startedAt" class="ask-time">{{ formatTime(item.startedAt) }}</span>
          </div>
          <p class="ask-question">{{ item.argsSummary || 'Agent 正在等待你的确认' }}</p>
          <div class="ask-actions">
            <el-input
              v-model="answers[item.id]"
              type="textarea"
              :rows="2"
              resize="none"
              placeholder="输入回复内容后提交"
              :disabled="submittingId === item.id"
            />
            <el-button
              type="primary"
              size="small"
              :loading="submittingId === item.id"
              :disabled="!(answers[item.id] || '').trim()"
              @click="submitAnswer(item)"
            >提交回复</el-button>
          </div>
        </div>
      </div>

      <div v-if="askTimedOut.length" class="ask-block">
        <h4>已超时（ask_user）</h4>
        <div v-for="item in askTimedOut" :key="item.id" class="ask-card timed-out">
          <div class="ask-head">
            <el-tag type="danger" size="small">等待超时</el-tag>
            <span class="ask-id">{{ item.id }}</span>
          </div>
          <p class="ask-question">{{ item.argsSummary || '等待用户回复超时' }}</p>
          <p class="ask-result">{{ item.resultSummary || '按超时策略失败，未静默继续' }}</p>
        </div>
      </div>

      <el-table v-loading="loading" :data="tools" size="small" class="tool-table">
        <el-table-column prop="id" label="调用号" width="100" show-overflow-tooltip />
        <el-table-column prop="toolName" label="工具" min-width="120" show-overflow-tooltip />
        <el-table-column label="风险" width="86">
          <template #default="{ row }">
            <el-tag :type="riskLevelType(row.riskLevel)" size="small">{{ riskLevelLabel(row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="toolStatusType(row.status)" size="small">{{ toolStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column prop="argsSummary" label="参数摘要" min-width="150" show-overflow-tooltip />
        <el-table-column prop="resultSummary" label="结果摘要" min-width="150" show-overflow-tooltip />
      </el-table>

      <el-empty
        v-if="!loading && !error && tools.length === 0"
        description="暂无工具调用记录"
        :image-size="72"
      />
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import agentRuntime, {
  isAskUserPending,
  isAskUserTimedOut,
  riskLevelLabel,
  riskLevelType,
  toolStatusLabel,
  toolStatusType
} from '../../api/agentRuntime'

const props = defineProps({
  runId: { type: [String, Number], required: true }
})

const loading = ref(false)
const tools = ref([])
const mocked = ref(false)
const forbidden = ref(false)
const error = ref('')
const answers = reactive({})
const submittingId = ref('')

const askPending = computed(() => tools.value.filter(isAskUserPending))
const askTimedOut = computed(() => tools.value.filter(isAskUserTimedOut))

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
    const response = await agentRuntime.getAgentRunTools(props.runId)
    const payload = response?.data?.data
    tools.value = Array.isArray(payload) ? payload : (payload?.records || payload?.list || [])
    mocked.value = Boolean(response?.data?.mock)
  } catch (err) {
    if (agentRuntime.isForbidden(err)) {
      forbidden.value = true
      tools.value = []
    } else {
      error.value = '工具调用列表加载失败，请稍后重试'
      tools.value = []
    }
  } finally {
    loading.value = false
  }
}

const submitAnswer = async (item) => {
  const answer = (answers[item.id] || '').trim()
  if (!answer) return
  submittingId.value = item.id
  try {
    await agentRuntime.respondAgentRunTool(props.runId, item.id, answer)
    ElMessage.success('回复已提交')
    answers[item.id] = ''
    await load()
  } catch (err) {
    if (agentRuntime.isForbidden(err)) {
      ElMessage.error('你没有回复该 ask_user 的权限')
    } else {
      ElMessage.error('回复提交失败，请稍后重试')
    }
  } finally {
    submittingId.value = ''
  }
}

watch(() => props.runId, () => { tools.value = []; load() })
onMounted(load)
</script>

<style scoped>
.tool-calls-panel { display: flex; flex-direction: column; gap: 12px; }
.panel-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.hint { color: var(--text-muted); font-size: 12px; }
.mock-alert { margin: 0; }
.ask-block h4 { margin: 4px 0 8px; font-size: 14px; }
.ask-card {
  border: 1px solid var(--glass-border);
  border-left: 3px solid var(--accent-orange);
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 10px;
  background: rgba(217, 119, 6, .06);
}
.ask-card.timed-out {
  border-left-color: var(--accent-red);
  background: rgba(220, 38, 38, .06);
}
.ask-head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.ask-id { font-family: var(--font-mono); font-size: 12px; color: var(--text-muted); }
.ask-time { margin-left: auto; font-size: 12px; color: var(--text-muted); }
.ask-question { margin: 0 0 10px; line-height: 1.6; color: var(--text-primary); }
.ask-result { margin: 0; font-size: 12px; color: var(--text-muted); }
.ask-actions { display: flex; flex-direction: column; gap: 8px; align-items: flex-end; }
.ask-actions .el-input { width: 100%; }
.tool-table { width: 100%; }
</style>
