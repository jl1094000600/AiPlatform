<template>
  <div class="page-container">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">执行历史</h2>
        <span class="total-count mono">{{ executions.length }} 条记录</span>
      </div>
      <div class="header-actions">
        <el-button @click="loadExecutions" class="refresh-btn">
          <Refresh class="btn-icon" /> 刷新
        </el-button>
      </div>
    </div>

    <!-- Execution List -->
    <div class="execution-list glass-card">
      <el-table
        :data="executions"
        v-loading="loading"
        stripe
        class="execution-table"
      >
        <el-table-column prop="executionId" label="执行ID" width="180" align="center">
          <template #default="{ row }">
            <span class="mono execution-id">{{ row.executionId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="workflowName" label="编排名称" min-width="160">
          <template #default="{ row }">
            <span class="workflow-name">{{ row.workflowName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发类型" width="100" align="center">
          <template #default="{ row }">
            <span class="trigger-badge" :class="row.triggerType?.toLowerCase()">
              {{ getTriggerText(row.triggerType) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="triggerSource" label="触发来源" min-width="120">
          <template #default="{ row }">
            <span class="text-muted">{{ row.triggerSource || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="status-badge" :class="row.status?.toLowerCase()">
              {{ getStatusText(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" min-width="160">
          <template #default="{ row }">
            <span class="mono">{{ formatTime(row.startTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="endTime" label="结束时间" min-width="160">
          <template #default="{ row }">
            <span class="mono">{{ formatTime(row.endTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="耗时" width="100" align="center">
          <template #default="{ row }">
            <span class="mono">{{ formatDuration(row.startTime, row.endTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Detail Dialog -->
    <el-dialog v-model="showDetail" title="执行详情" width="700px" class="detail-dialog">
      <div v-if="selectedExecution" class="detail-content">
        <div class="detail-section">
          <h4>基本信息</h4>
          <div class="detail-grid">
            <div class="detail-item">
              <label>执行ID</label>
              <span class="mono">{{ selectedExecution.executionId }}</span>
            </div>
            <div class="detail-item">
              <label>编排名称</label>
              <span>{{ selectedExecution.workflowName }}</span>
            </div>
            <div class="detail-item">
              <label>触发类型</label>
              <span>{{ getTriggerText(selectedExecution.triggerType) }}</span>
            </div>
            <div class="detail-item">
              <label>状态</label>
              <span class="status-badge" :class="selectedExecution.status?.toLowerCase()">
                {{ getStatusText(selectedExecution.status) }}
              </span>
            </div>
          </div>
        </div>

        <div class="detail-section" v-if="selectedExecution.startParams">
          <h4>启动参数</h4>
          <pre class="json-preview">{{ formatJson(selectedExecution.startParams) }}</pre>
        </div>

        <div class="detail-section" v-if="selectedExecution.result">
          <h4>执行结果</h4>
          <pre class="json-preview">{{ formatJson(selectedExecution.result) }}</pre>
        </div>

        <div class="detail-section" v-if="selectedExecution.errorMessage">
          <h4>错误信息</h4>
          <div class="error-message">{{ selectedExecution.errorMessage }}</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const executions = ref([])
const loading = ref(false)
const showDetail = ref(false)
const selectedExecution = ref(null)

const getTriggerText = (type) => {
  const map = { MANUAL: '手动', SCHEDULE: '定时', EVENT: '事件' }
  return map[type] || type
}

const getStatusText = (status) => {
  const map = {
    PENDING: '等待中',
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

const formatTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
}

const formatDuration = (startTime, endTime) => {
  if (!startTime || !endTime) return '-'
  const start = new Date(startTime)
  const end = new Date(endTime)
  const diff = end - start
  if (diff < 1000) return diff + 'ms'
  if (diff < 60000) return (diff / 1000).toFixed(1) + 's'
  return (diff / 60000).toFixed(1) + 'm'
}

const formatJson = (str) => {
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

const loadExecutions = async () => {
  loading.value = true
  try {
    const res = await api.getAllWorkflowExecutions()
    if (res.data.code === 200) {
      executions.value = res.data.data || []
    }
  } catch (e) {
    console.error('加载执行历史失败', e)
    ElMessage.error('加载执行历史失败')
  } finally {
    loading.value = false
  }
}

const viewDetail = (row) => {
  selectedExecution.value = row
  showDetail.value = true
}

onMounted(() => {
  loadExecutions()
})
</script>

<style scoped>
.page-container {
  animation: fadeInUp 0.4s ease;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 16px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
}

.total-count {
  font-size: 13px;
  color: var(--text-muted);
}

.header-actions {
  display: flex;
  gap: 12px;
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
}

.btn-icon {
  width: 14px;
  height: 14px;
}

/* Execution List */
.execution-list {
  padding: 20px;
}

.execution-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(26, 31, 78, 0.5);
}

.execution-id {
  color: var(--accent-cyan);
}

.workflow-name {
  font-weight: 500;
}

.trigger-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
}

.trigger-badge.manual {
  background: rgba(0, 240, 255, 0.15);
  color: var(--accent-cyan);
}

.trigger-badge.schedule {
  background: rgba(245, 158, 11, 0.15);
  color: var(--accent-amber);
}

.trigger-badge.event {
  background: rgba(124, 58, 237, 0.15);
  color: var(--accent-purple);
}

.status-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
}

.status-badge.pending {
  background: rgba(245, 158, 11, 0.15);
  color: var(--accent-amber);
}

.status-badge.running {
  background: rgba(0, 240, 255, 0.15);
  color: var(--accent-cyan);
}

.status-badge.completed {
  background: rgba(16, 185, 129, 0.15);
  color: var(--accent-green);
}

.status-badge.failed {
  background: rgba(239, 68, 68, 0.15);
  color: var(--accent-red);
}

.status-badge.cancelled {
  background: rgba(156, 163, 175, 0.15);
  color: #9ca3af;
}

/* Detail Dialog */
.detail-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-section h4 {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-item label {
  font-size: 11px;
  color: var(--text-muted);
}

.json-preview {
  background: rgba(10, 14, 39, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 12px;
  font-size: 12px;
  color: var(--accent-cyan);
  overflow-x: auto;
  max-height: 200px;
  overflow-y: auto;
}

.error-message {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 8px;
  padding: 12px;
  font-size: 12px;
  color: var(--accent-red);
}
</style>
