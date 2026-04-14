<template>
  <div class="page-container">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">接口调用监控</h2>
        <span class="total-count mono">共 {{ total }} 条记录</span>
      </div>
      <div class="header-actions">
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          class="date-range-picker"
        />
        <el-button type="primary" @click="loadRecords" class="query-btn">
          <Search class="btn-icon" /> 查询
        </el-button>
      </div>
    </div>

    <!-- Stats Cards -->
    <div class="stats-grid stagger-children">
      <div class="stat-card glass-card">
        <div class="stat-icon total">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M12 2L2 7l10 5 10-5-10-5z" stroke="currentColor" stroke-width="2" fill="none"/>
            <path d="M2 17l10 5 10-5" stroke="currentColor" stroke-width="2"/>
            <path d="M2 12l10 5 10-5" stroke="currentColor" stroke-width="2"/>
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value mono">{{ stats.totalCalls }}</span>
          <span class="stat-label">总调用次数</span>
        </div>
      </div>
      <div class="stat-card glass-card">
        <div class="stat-icon success">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/>
            <path d="M9 12l2 2 4-4" stroke="currentColor" stroke-width="2"/>
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value mono">{{ stats.successRate }}%</span>
          <span class="stat-label">成功率</span>
        </div>
      </div>
      <div class="stat-card glass-card">
        <div class="stat-icon avg-time">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/>
            <path d="M12 6v6l4 2" stroke="currentColor" stroke-width="2"/>
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value mono">{{ stats.avgDuration }}ms</span>
          <span class="stat-label">平均响应时间</span>
        </div>
      </div>
    </div>

    <!-- Table Card -->
    <div class="glass-card table-card">
      <el-table
        :data="records"
        v-loading="loading"
        stripe
        class="records-table"
      >
        <el-table-column prop="traceId" label="TraceID" width="200">
          <template #default="{ row }">
            <span class="mono trace-id">{{ row.traceId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="agentId" label="Agent ID" width="100" align="center">
          <template #default="{ row }">
            <span class="mono">#{{ row.agentId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="响应时间" width="120" align="center">
          <template #default="{ row }">
            <span class="duration-cell mono" :class="getDurationClass(row.durationMs)">
              {{ row.durationMs ? row.durationMs + 'ms' : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="inputTokens" label="输入Token" width="100" align="center">
          <template #default="{ row }">
            <span class="mono">{{ row.inputTokens ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="outputTokens" label="输出Token" width="100" align="center">
          <template #default="{ row }">
            <span class="mono">{{ row.outputTokens ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="statusCode" label="状态码" width="80" align="center">
          <template #default="{ row }">
            <span class="status-code mono" :class="getStatusCodeClass(row.statusCode)">
              {{ row.statusCode || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="success" label="状态" width="80" align="center">
          <template #default="{ row }">
            <span class="result-badge" :class="row.success === 1 ? 'success' : 'failed'">
              {{ row.success === 1 ? '成功' : '失败' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="调用时间" min-width="160">
          <template #default="{ row }">
            <span class="mono time-cell">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          @current-change="loadRecords"
          layout="total, prev, pager, next"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import api from '../api'

const records = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const dateRange = ref([])

const stats = reactive({
  totalCalls: 0,
  successRate: 0,
  avgDuration: 0
})

const getDurationClass = (ms) => {
  if (!ms) return ''
  if (ms < 100) return 'fast'
  if (ms < 500) return 'normal'
  return 'slow'
}

const getStatusCodeClass = (code) => {
  if (!code) return ''
  if (code >= 200 && code < 300) return 'success'
  if (code >= 400 && code < 500) return 'client-error'
  if (code >= 500) return 'server-error'
  return ''
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

const loadRecords = async () => {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0].toISOString()
      params.endTime = dateRange.value[1].toISOString()
    }
    const res = await api.getCallRecords(params)
    if (res.data.code === 200) {
      records.value = res.data.data.records || []
      total.value = res.data.data.total || 0
      stats.totalCalls = res.data.data.total || 0
      stats.successRate = res.data.data.successRate || 0
      stats.avgDuration = res.data.data.avgDuration || 0
    }
  } catch (e) {
    console.error('加载失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(loadRecords)
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
  align-items: center;
}

.date-range-picker {
  width: 360px;
}

.query-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
}

.btn-icon {
  width: 14px;
  height: 14px;
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  animation: fadeInUp 0.5s ease forwards;
  opacity: 0;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-icon.total {
  background: rgba(0, 240, 255, 0.15);
  color: var(--accent-cyan);
}

.stat-icon.success {
  background: rgba(16, 185, 129, 0.15);
  color: var(--accent-green);
}

.stat-icon.avg-time {
  background: rgba(139, 92, 246, 0.15);
  color: var(--accent-purple);
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1;
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
}

/* Table */
.table-card {
  overflow: hidden;
}

.records-table {
  --el-table-bg-color: transparent;
}

.trace-id {
  font-size: 12px;
  color: var(--text-secondary);
}

.duration-cell {
  font-size: 13px;
  padding: 4px 8px;
  border-radius: 4px;
}

.duration-cell.fast {
  color: var(--accent-green);
  background: rgba(16, 185, 129, 0.1);
}

.duration-cell.normal {
  color: var(--accent-orange);
  background: rgba(245, 158, 11, 0.1);
}

.duration-cell.slow {
  color: var(--accent-red);
  background: rgba(239, 68, 68, 0.1);
}

.status-code {
  font-size: 12px;
  padding: 3px 8px;
  border-radius: 4px;
}

.status-code.success {
  color: var(--accent-green);
  background: rgba(16, 185, 129, 0.1);
}

.status-code.client-error {
  color: var(--accent-orange);
  background: rgba(245, 158, 11, 0.1);
}

.status-code.server-error {
  color: var(--accent-red);
  background: rgba(239, 68, 68, 0.1);
}

.result-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.result-badge.success {
  background: rgba(16, 185, 129, 0.15);
  color: var(--accent-green);
}

.result-badge.failed {
  background: rgba(239, 68, 68, 0.15);
  color: var(--accent-red);
}

.time-cell {
  font-size: 12px;
  color: var(--text-muted);
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 24px 0 16px;
  border-top: 1px solid var(--border-color);
}
</style>
