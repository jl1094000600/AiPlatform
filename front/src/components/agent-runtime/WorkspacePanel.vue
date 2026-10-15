<template>
  <div class="workspace-panel">
    <div class="panel-toolbar">
      <div class="status-line">
        <span class="hint">沙箱状态</span>
        <el-tag :type="statusType(workspace.status)" size="small">{{ statusLabel(workspace.status) }}</el-tag>
      </div>
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
      title="你没有查看该运行 Workspace 的权限"
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

    <template v-if="!forbidden">
      <div class="workdir">
        <span>工作目录</span>
        <code>{{ workspace.workDir || '-' }}</code>
      </div>

      <div class="files-block">
        <div class="block-head">
          <h4>文件列表</h4>
          <span class="hint">共 {{ files.length }} 个文件</span>
        </div>
        <el-table :data="files" size="small" max-height="260">
          <el-table-column prop="path" label="路径" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="file-path">{{ row.path }}</span>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="110">
            <template #default="{ row }">{{ formatSize(row.size) }}</template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="!loading && !error && files.length === 0"
          description="暂无文件"
          :image-size="64"
        />
      </div>

      <div class="logs-block">
        <div class="block-head">
          <h4>最近日志</h4>
          <el-button link type="primary" size="small" @click="logsOpen = !logsOpen">
            {{ logsOpen ? '收起' : '展开' }}
          </el-button>
        </div>
        <div v-show="logsOpen" class="logs-scroll">
          <pre v-if="logsText">{{ logsText }}</pre>
          <el-empty v-else description="暂无日志" :image-size="56" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import agentRuntime from '../../api/agentRuntime'

const props = defineProps({
  runId: { type: [String, Number], required: true }
})

const loading = ref(false)
const workspace = ref({})
const mocked = ref(false)
const forbidden = ref(false)
const error = ref('')
const logsOpen = ref(true)

const files = computed(() => {
  const list = workspace.value?.files
  return Array.isArray(list) ? list : []
})

const logsText = computed(() => {
  const logs = workspace.value?.recentLogs
  if (Array.isArray(logs)) return logs.join('\n')
  return typeof logs === 'string' ? logs : ''
})

const statusLabel = (status) => ({
  CREATED: '已创建',
  STARTING: '启动中',
  RUNNING: '运行中',
  READY: '就绪',
  STOPPED: '已停止',
  STOPPING: '停止中',
  FAILED: '失败',
  TIMEOUT: '已超时',
  DESTROYED: '已回收'
})[status] || status || '未知'

const statusType = (status) => ({
  CREATED: 'info',
  STARTING: 'info',
  RUNNING: 'primary',
  READY: 'success',
  STOPPED: 'info',
  STOPPING: 'warning',
  FAILED: 'danger',
  TIMEOUT: 'danger',
  DESTROYED: 'info'
})[status] || 'info'

const formatSize = (size) => {
  const n = Number(size)
  if (!Number.isFinite(n) || n < 0) return '-'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / (1024 * 1024)).toFixed(1) + ' MB'
}

const load = async () => {
  if (!props.runId) return
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const response = await agentRuntime.getWorkspace(props.runId)
    workspace.value = response?.data?.data || {}
    mocked.value = Boolean(response?.data?.mock)
  } catch (err) {
    if (agentRuntime.isForbidden(err)) {
      forbidden.value = true
      workspace.value = {}
    } else {
      error.value = 'Workspace 信息加载失败，请稍后重试'
      workspace.value = {}
    }
  } finally {
    loading.value = false
  }
}

watch(() => props.runId, () => { workspace.value = {}; load() })
onMounted(load)
</script>

<style scoped>
.workspace-panel { display: flex; flex-direction: column; gap: 12px; }
.panel-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.status-line { display: flex; align-items: center; gap: 10px; }
.hint { color: var(--text-muted); font-size: 12px; }
.workdir {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(148, 163, 184, .08);
  font-size: 13px;
}
.workdir span { color: var(--text-muted); flex-shrink: 0; }
.workdir code {
  font-family: var(--font-mono);
  word-break: break-all;
  color: var(--text-primary);
}
.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}
.block-head h4 { margin: 0; font-size: 14px; }
.file-path { font-family: var(--font-mono); font-size: 12px; }
.logs-scroll {
  max-height: 220px;
  overflow: auto;
  border: 1px solid var(--glass-border);
  border-radius: 8px;
  background: rgba(15, 23, 42, .04);
  padding: 10px 12px;
}
.logs-scroll pre {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-secondary);
}
</style>
